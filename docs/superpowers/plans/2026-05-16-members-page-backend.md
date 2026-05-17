# Members Page — Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add pagination to `GET /api/v1/members` and implement 3 missing invitation endpoints (`GET /api/v1/invitations`, `POST /api/v1/invitations/{id}/resend`, `DELETE /api/v1/invitations/{id}`) required by the Members page UI spec.

**Architecture:** All changes stay within the `invitation` and `member` packages following the existing Controller → Service → Repository slice pattern. A shared `PagedResponse<T>` record goes in `common` and is reused by both packages. No new dependencies needed — Spring Data's `Pageable` / `Page` are already on the classpath.

**Tech Stack:** Java 21, Spring Boot 3.3, Spring Data JPA, JUnit 5, AssertJ, Testcontainers (via `BaseIT`), TestRestTemplate.

---

## File Map

| Status | File | Change |
|--------|------|--------|
| CREATE | `common/PagedResponse.java` | Generic paginated response wrapper |
| CREATE | `invitation/InvitationNotFoundException.java` | 404 exception for missing/foreign invitation |
| MODIFY | `member/MemberService.java` | Add `Pageable` parameter to `listAll` |
| MODIFY | `member/MemberServiceImpl.java` | Implement paginated `listAll` |
| MODIFY | `member/MemberController.java` | Accept `Pageable`, return `PagedResponse` |
| MODIFY | `invitation/InvitationRepository.java` | Add `findAllPending` query |
| MODIFY | `invitation/InvitationService.java` | Add `listPending`, `resend`, `revoke` |
| MODIFY | `invitation/InvitationServiceImpl.java` | Implement 3 new methods |
| MODIFY | `invitation/InvitationController.java` | Add 3 new endpoints + `@RequestMapping` |
| MODIFY | `test/member/MemberManagementIT.java` | Update `listMembers` test for `PagedResponse` |
| CREATE | `test/invitation/InvitationManagementIT.java` | Tests for the 3 new endpoints |

All paths relative to `src/main/java/com/leonardtrinh/supportsaas/` and `src/test/java/com/leonardtrinh/supportsaas/`.

---

## Task 1: `PagedResponse<T>` — shared pagination wrapper

**Files:**
- Create: `src/main/java/com/leonardtrinh/supportsaas/common/PagedResponse.java`

- [ ] **Step 1.1: Create `PagedResponse` record**

```java
package com.leonardtrinh.supportsaas.common;

import org.springframework.data.domain.Page;
import java.util.List;

public record PagedResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public static <T> PagedResponse<T> from(Page<T> p) {
        return new PagedResponse<>(
            p.getContent(),
            p.getNumber(),
            p.getSize(),
            p.getTotalElements(),
            p.getTotalPages()
        );
    }
}
```

- [ ] **Step 1.2: Commit**

```bash
git add src/main/java/com/leonardtrinh/supportsaas/common/PagedResponse.java
git commit -m "feat(common): add PagedResponse wrapper for paginated API responses"
```

---

## Task 2: Paginate `GET /api/v1/members`

**Files:**
- Modify: `src/main/java/com/leonardtrinh/supportsaas/member/MemberService.java`
- Modify: `src/main/java/com/leonardtrinh/supportsaas/member/MemberServiceImpl.java`
- Modify: `src/main/java/com/leonardtrinh/supportsaas/member/MemberController.java`
- Modify: `src/test/java/com/leonardtrinh/supportsaas/member/MemberManagementIT.java`

- [ ] **Step 2.1: Write failing test first**

In `MemberManagementIT.java`, replace the existing `listMembers_returnsOnlyTenantMembers` test:

```java
// Add import at top of file:
import com.leonardtrinh.supportsaas.common.PagedResponse;

@Test
@DisplayName("listMembers: returns paginated members scoped to caller's tenant")
void listMembers_returnsPaginatedTenantMembers() {
    String u1 = UUID.randomUUID().toString().substring(0, 8);
    String u2 = UUID.randomUUID().toString().substring(0, 8);
    AuthResponse tenant1 = doSignup("TenantA " + u1, "ownerA+" + u1 + "@example.com");
    AuthResponse tenant2 = doSignup("TenantB " + u2, "ownerB+" + u2 + "@example.com");

    // Add a member to tenant1
    String extra = "extra+" + u1 + "@example.com";
    String rawToken = insertTestInvitation(tenant1.businessId(), extra, Role.MEMBER,
            Instant.now().plus(7, ChronoUnit.DAYS));
    restTemplate.exchange("/api/v1/invitations/accept", HttpMethod.POST,
            new HttpEntity<>(new AcceptInvitationRequest(rawToken, "password123")),
            new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {});

    ResponseEntity<ApiResponse<PagedResponse<MemberResponse>>> resp1 = restTemplate.exchange(
            "/api/v1/members?page=0&size=10", HttpMethod.GET,
            new HttpEntity<>(authHeader(tenant1.accessToken())),
            new ParameterizedTypeReference<>() {}
    );
    ResponseEntity<ApiResponse<PagedResponse<MemberResponse>>> resp2 = restTemplate.exchange(
            "/api/v1/members?page=0&size=10", HttpMethod.GET,
            new HttpEntity<>(authHeader(tenant2.accessToken())),
            new ParameterizedTypeReference<>() {}
    );

    assertThat(resp1.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resp2.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resp1.getBody().data().content()).hasSize(2);
    assertThat(resp1.getBody().data().totalElements()).isEqualTo(2);
    assertThat(resp2.getBody().data().content()).hasSize(1);
}
```

- [ ] **Step 2.2: Run test — expect compile failure**

```bash
mvn test -Dtest=MemberManagementIT#listMembers_returnsPaginatedTenantMembers -pl . 2>&1 | tail -20
```

Expected: compilation error — `PagedResponse` not used in `MemberController` yet.

- [ ] **Step 2.3: Update `MemberService` interface**

```java
package com.leonardtrinh.supportsaas.member;

import com.leonardtrinh.supportsaas.auth.JwtClaims;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface MemberService {

    Page<MemberResponse> listAll(Pageable pageable);

    MemberResponse getById(UUID id);

    void delete(UUID targetMemberId, JwtClaims caller);

    MemberResponse changeRole(UUID targetMemberId, UpdateRoleRequest request, JwtClaims caller);
}
```

- [ ] **Step 2.4: Update `MemberServiceImpl`**

Replace the `listAll()` method (keep all other methods unchanged):

```java
// Add imports:
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Override
public Page<MemberResponse> listAll(Pageable pageable) {
    return memberRepository.findAll(pageable)
            .map(MemberResponse::from);
}
```

Note: `MemberRepository extends JpaRepository<Member, UUID>` — `findAll(Pageable)` is already available. The Hibernate tenant filter automatically scopes results to the current tenant.

- [ ] **Step 2.5: Update `MemberController`**

Replace `listAll()` endpoint (keep all other endpoints unchanged):

```java
// Add imports:
import com.leonardtrinh.supportsaas.common.PagedResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

@GetMapping
@Operation(summary = "List all members in the tenant (paginated)")
@ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Member page returned"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient role")
})
public ApiResponse<PagedResponse<MemberResponse>> listAll(
        @PageableDefault(size = 10) Pageable pageable) {
    requireAdminOrOwner();
    return ApiResponse.ok(PagedResponse.from(memberService.listAll(pageable)));
}
```

- [ ] **Step 2.6: Run test — expect pass**

```bash
mvn test -Dtest=MemberManagementIT -pl . 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`, all `MemberManagementIT` tests green.

- [ ] **Step 2.7: Commit**

```bash
git add src/main/java/com/leonardtrinh/supportsaas/member/MemberService.java \
        src/main/java/com/leonardtrinh/supportsaas/member/MemberServiceImpl.java \
        src/main/java/com/leonardtrinh/supportsaas/member/MemberController.java \
        src/test/java/com/leonardtrinh/supportsaas/member/MemberManagementIT.java
git commit -m "feat(member): paginate GET /api/v1/members with PagedResponse"
```

---

## Task 3: `InvitationNotFoundException`

**Files:**
- Create: `src/main/java/com/leonardtrinh/supportsaas/invitation/InvitationNotFoundException.java`

- [ ] **Step 3.1: Create exception**

```java
package com.leonardtrinh.supportsaas.invitation;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class InvitationNotFoundException extends AppException {

    public InvitationNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND, "INVITATION_NOT_FOUND", "Invitation not found: id=" + id);
    }
}
```

`GlobalExceptionHandler.handleAppException` already handles all `AppException` subclasses — no changes needed there.

- [ ] **Step 3.2: Commit**

```bash
git add src/main/java/com/leonardtrinh/supportsaas/invitation/InvitationNotFoundException.java
git commit -m "feat(invitation): add InvitationNotFoundException"
```

---

## Task 4: `GET /api/v1/invitations` — list pending invitations

**Files:**
- Modify: `src/main/java/com/leonardtrinh/supportsaas/invitation/InvitationRepository.java`
- Modify: `src/main/java/com/leonardtrinh/supportsaas/invitation/InvitationService.java`
- Modify: `src/main/java/com/leonardtrinh/supportsaas/invitation/InvitationServiceImpl.java`
- Modify: `src/main/java/com/leonardtrinh/supportsaas/invitation/InvitationController.java`
- Create: `src/test/java/com/leonardtrinh/supportsaas/invitation/InvitationManagementIT.java`

- [ ] **Step 4.1: Write failing test**

Create `src/test/java/com/leonardtrinh/supportsaas/invitation/InvitationManagementIT.java`:

```java
package com.leonardtrinh.supportsaas.invitation;

import com.leonardtrinh.supportsaas.auth.AuthResponse;
import com.leonardtrinh.supportsaas.common.ApiResponse;
import com.leonardtrinh.supportsaas.common.BaseIT;
import com.leonardtrinh.supportsaas.common.PagedResponse;
import com.leonardtrinh.supportsaas.member.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InvitationManagementIT extends BaseIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        configureRestTemplate();
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private String insertTestInvitation(UUID businessId, String email, Role role, Instant expiresAt) {
        String rawToken = "test-token-" + UUID.randomUUID();
        String tokenHash = sha256Hex(rawToken);
        jdbcTemplate.update(
                "INSERT INTO invitations (business_id, email, role, token_hash, expires_at) VALUES (?, ?, ?, ?, ?)",
                businessId, email, role.name(), tokenHash,
                java.sql.Timestamp.from(expiresAt));
        return rawToken;
    }

    private String sha256Hex(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------------------------------------------------------------
    // GET /api/v1/invitations
    // ---------------------------------------------------------------

    @Test
    @DisplayName("listPending: returns only pending (non-accepted, non-expired) invitations for caller's tenant")
    void listPending_returnsOnlyPendingForTenant() {
        String u = UUID.randomUUID().toString().substring(0, 8);
        AuthResponse owner = doSignup("ListCo " + u, "listowner+" + u + "@example.com");

        // Insert 2 pending invitations for this tenant
        insertTestInvitation(owner.businessId(), "pending1+" + u + "@example.com",
                Role.MEMBER, Instant.now().plus(7, ChronoUnit.DAYS));
        insertTestInvitation(owner.businessId(), "pending2+" + u + "@example.com",
                Role.ADMIN, Instant.now().plus(7, ChronoUnit.DAYS));
        // Insert 1 expired — should NOT appear
        insertTestInvitation(owner.businessId(), "expired+" + u + "@example.com",
                Role.MEMBER, Instant.now().minus(1, ChronoUnit.DAYS));

        ResponseEntity<ApiResponse<PagedResponse<InvitationResponse>>> resp = restTemplate.exchange(
                "/api/v1/invitations?page=0&size=10", HttpMethod.GET,
                new HttpEntity<>(authHeader(owner.accessToken())),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().data().totalElements()).isEqualTo(2);
        assertThat(resp.getBody().data().content())
                .extracting(InvitationResponse::email)
                .doesNotContain("expired+" + u + "@example.com");
    }

    @Test
    @DisplayName("listPending: tenant isolation — cannot see other tenant's invitations")
    void listPending_tenantIsolation() {
        String u1 = UUID.randomUUID().toString().substring(0, 8);
        String u2 = UUID.randomUUID().toString().substring(0, 8);
        AuthResponse tenant1 = doSignup("IsoA " + u1, "isoA+" + u1 + "@example.com");
        AuthResponse tenant2 = doSignup("IsoB " + u2, "isoB+" + u2 + "@example.com");

        insertTestInvitation(tenant1.businessId(), "inv+" + u1 + "@example.com",
                Role.MEMBER, Instant.now().plus(7, ChronoUnit.DAYS));

        ResponseEntity<ApiResponse<PagedResponse<InvitationResponse>>> resp = restTemplate.exchange(
                "/api/v1/invitations?page=0&size=10", HttpMethod.GET,
                new HttpEntity<>(authHeader(tenant2.accessToken())),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().data().totalElements()).isEqualTo(0);
    }
}
```

- [ ] **Step 4.2: Run test — expect 404 (endpoint not yet mapped)**

```bash
mvn test -Dtest=InvitationManagementIT#listPending_returnsOnlyPendingForTenant -pl . 2>&1 | tail -20
```

Expected: FAIL — `404 NOT_FOUND` or compilation error.

- [ ] **Step 4.3: Add `findAllPending` to `InvitationRepository`**

```java
// Add imports:
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.Instant;

// Add method:
@Query("SELECT i FROM Invitation i WHERE i.acceptedAt IS NULL AND i.expiresAt > :now")
Page<Invitation> findAllPending(@Param("now") Instant now, Pageable pageable);
```

The Hibernate tenant filter automatically adds `WHERE business_id = :tenantId` when `TenantContext` is set.

- [ ] **Step 4.4: Add `listPending` to `InvitationService`**

```java
// Add import:
import com.leonardtrinh.supportsaas.auth.JwtClaims;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

// Add method:
Page<InvitationResponse> listPending(Pageable pageable, JwtClaims caller);
```

- [ ] **Step 4.5: Implement `listPending` in `InvitationServiceImpl`**

```java
// Add import:
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.Instant;

// Add method:
@Override
public Page<InvitationResponse> listPending(Pageable pageable, JwtClaims caller) {
    Role callerRole = Role.valueOf(caller.role());
    if (callerRole != Role.ADMIN && callerRole != Role.OWNER) {
        throw new AccessDeniedException("Only ADMIN or OWNER can list invitations");
    }
    return invitationRepository.findAllPending(Instant.now(), pageable)
            .map(i -> new InvitationResponse(
                    i.getId(), i.getEmail(), i.getRole(), i.getExpiresAt(), i.getCreatedAt()));
}
```

- [ ] **Step 4.6: Add `GET /api/v1/invitations` to `InvitationController`**

Replace the entire `InvitationController` (add `@RequestMapping` + new endpoint, keep existing methods):

```java
package com.leonardtrinh.supportsaas.invitation;

import com.leonardtrinh.supportsaas.auth.AuthResponse;
import com.leonardtrinh.supportsaas.auth.JwtClaims;
import com.leonardtrinh.supportsaas.common.ApiResponse;
import com.leonardtrinh.supportsaas.common.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/invitations")
@Tag(name = "Invitations", description = "Invite members and accept invitations")
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping("/accept")
    @Operation(summary = "Accept a member invitation and set password")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Invitation accepted, JWT tokens returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid or expired invitation token")
    })
    public ApiResponse<AuthResponse> accept(@Valid @RequestBody AcceptInvitationRequest request) {
        AuthResponse response = invitationService.accept(request);
        return ApiResponse.ok(response);
    }

    @GetMapping
    @SecurityRequirement(name = "Bearer")
    @Operation(summary = "List pending invitations for the caller's tenant")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Pending invitations returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient role")
    })
    public ApiResponse<PagedResponse<InvitationResponse>> listPending(
            @PageableDefault(size = 10) Pageable pageable) {
        JwtClaims caller = caller();
        return ApiResponse.ok(PagedResponse.from(invitationService.listPending(pageable, caller)));
    }

    // ── existing invite endpoint — path now relative to /api/v1/invitations ──
    @PostMapping("/invite")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @SecurityRequirement(name = "Bearer")
    @Operation(summary = "Invite a new member to the tenant")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Invitation sent"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Member already exists")
    })
    public ApiResponse<InvitationResponse> invite(@Valid @RequestBody InviteRequest request) {
        JwtClaims caller = caller();
        InvitationResponse response = invitationService.invite(request, caller);
        return ApiResponse.ok(response);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private JwtClaims caller() {
        return (JwtClaims) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
```

**Important:** The existing `POST /api/v1/members/invite` path moves to `POST /api/v1/invitations/invite`. Update the existing `MemberManagementIT` invite calls from `/api/v1/members/invite` to `/api/v1/invitations/invite`.

- [ ] **Step 4.7: Update `MemberManagementIT` invite path**

In `MemberManagementIT.java`, replace all occurrences of `/api/v1/members/invite` with `/api/v1/invitations/invite`.

- [ ] **Step 4.8: Run all tests — expect pass**

```bash
mvn test -Dtest="MemberManagementIT,InvitationManagementIT" -pl . 2>&1 | tail -30
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4.9: Commit**

```bash
git add src/main/java/com/leonardtrinh/supportsaas/invitation/ \
        src/test/java/com/leonardtrinh/supportsaas/invitation/InvitationManagementIT.java \
        src/test/java/com/leonardtrinh/supportsaas/member/MemberManagementIT.java
git commit -m "feat(invitation): add GET /api/v1/invitations — list pending invitations paginated"
```

---

## Task 5: `POST /api/v1/invitations/{id}/resend`

**Files:**
- Modify: `src/main/java/com/leonardtrinh/supportsaas/invitation/InvitationService.java`
- Modify: `src/main/java/com/leonardtrinh/supportsaas/invitation/InvitationServiceImpl.java`
- Modify: `src/main/java/com/leonardtrinh/supportsaas/invitation/InvitationController.java`
- Modify: `src/test/java/com/leonardtrinh/supportsaas/invitation/InvitationManagementIT.java`

- [ ] **Step 5.1: Write failing tests — add to `InvitationManagementIT`**

```java
// ── POST /api/v1/invitations/{id}/resend ──

@Test
@DisplayName("resend: resets expiry and returns updated invitation")
void resend_validId_resetsExpiry() {
    String u = UUID.randomUUID().toString().substring(0, 8);
    AuthResponse owner = doSignup("ResendCo " + u, "resendowner+" + u + "@example.com");

    // Create a pending invitation via the API to get its ID
    InviteRequest req = new InviteRequest("toresend+" + u + "@example.com", Role.MEMBER);
    ResponseEntity<ApiResponse<InvitationResponse>> invResp = restTemplate.exchange(
            "/api/v1/invitations/invite", HttpMethod.POST,
            new HttpEntity<>(req, authHeader(owner.accessToken())),
            new ParameterizedTypeReference<>() {}
    );
    UUID invId = invResp.getBody().data().id();
    Instant originalExpiry = invResp.getBody().data().expiresAt();

    // Resend
    ResponseEntity<ApiResponse<InvitationResponse>> resp = restTemplate.exchange(
            "/api/v1/invitations/" + invId + "/resend", HttpMethod.POST,
            new HttpEntity<>(authHeader(owner.accessToken())),
            new ParameterizedTypeReference<>() {}
    );

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resp.getBody().data().expiresAt()).isAfter(originalExpiry);
}

@Test
@DisplayName("resend: non-existent id returns 404")
void resend_unknownId_returns404() {
    String u = UUID.randomUUID().toString().substring(0, 8);
    AuthResponse owner = doSignup("Resend404Co " + u, "r404+" + u + "@example.com");

    ResponseEntity<Object> resp = restTemplate.exchange(
            "/api/v1/invitations/" + UUID.randomUUID() + "/resend", HttpMethod.POST,
            new HttpEntity<>(authHeader(owner.accessToken())),
            new ParameterizedTypeReference<>() {}
    );

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
}
```

- [ ] **Step 5.2: Run tests — expect 404 (endpoint not yet mapped)**

```bash
mvn test -Dtest=InvitationManagementIT#resend_validId_resetsExpiry -pl . 2>&1 | tail -10
```

Expected: FAIL.

- [ ] **Step 5.3: Add `resend` to `InvitationService`**

```java
InvitationResponse resend(UUID id, JwtClaims caller);
```

- [ ] **Step 5.4: Implement `resend` in `InvitationServiceImpl`**

```java
@Override
@Transactional
public InvitationResponse resend(UUID id, JwtClaims caller) {
    Role callerRole = Role.valueOf(caller.role());
    if (callerRole != Role.ADMIN && callerRole != Role.OWNER) {
        throw new AccessDeniedException("Only ADMIN or OWNER can resend invitations");
    }

    // findById uses the Hibernate tenant filter — returns empty if invitation belongs to another tenant
    Invitation invitation = invitationRepository.findById(id)
            .orElseThrow(() -> new InvitationNotFoundException(id));

    if (invitation.getAcceptedAt() != null) {
        throw new InvitationNotFoundException(id); // already accepted — treat as not found
    }

    // Generate new token and reset expiry
    String rawToken = tokenGenerator.generateRawToken();
    invitation.setTokenHash(tokenGenerator.hash(rawToken));
    invitation.setExpiresAt(Instant.now().plus(72, ChronoUnit.HOURS));
    Invitation saved = invitationRepository.save(invitation);

    asyncEmailSender.sendInvitationEmail(saved.getEmail(), rawToken);

    return new InvitationResponse(
            saved.getId(), saved.getEmail(), saved.getRole(),
            saved.getExpiresAt(), saved.getCreatedAt());
}
```

- [ ] **Step 5.5: Add endpoint to `InvitationController`**

Add inside `InvitationController`, before the closing brace:

```java
@PostMapping("/{id}/resend")
@SecurityRequirement(name = "Bearer")
@Operation(summary = "Resend a pending invitation email and reset expiry to 72 hours")
@ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Invitation resent"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient role"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Invitation not found or already accepted")
})
public ApiResponse<InvitationResponse> resend(@PathVariable UUID id) {
    return ApiResponse.ok(invitationService.resend(id, caller()));
}
```

- [ ] **Step 5.6: Run tests — expect pass**

```bash
mvn test -Dtest=InvitationManagementIT -pl . 2>&1 | tail -20
```

Expected: all `InvitationManagementIT` tests green.

- [ ] **Step 5.7: Commit**

```bash
git add src/main/java/com/leonardtrinh/supportsaas/invitation/ \
        src/test/java/com/leonardtrinh/supportsaas/invitation/InvitationManagementIT.java
git commit -m "feat(invitation): add POST /api/v1/invitations/{id}/resend"
```

---

## Task 6: `DELETE /api/v1/invitations/{id}` — revoke

**Files:**
- Modify: `src/main/java/com/leonardtrinh/supportsaas/invitation/InvitationService.java`
- Modify: `src/main/java/com/leonardtrinh/supportsaas/invitation/InvitationServiceImpl.java`
- Modify: `src/main/java/com/leonardtrinh/supportsaas/invitation/InvitationController.java`
- Modify: `src/test/java/com/leonardtrinh/supportsaas/invitation/InvitationManagementIT.java`

- [ ] **Step 6.1: Write failing tests — add to `InvitationManagementIT`**

```java
// ── DELETE /api/v1/invitations/{id} ──

@Test
@DisplayName("revoke: deletes pending invitation, returns 204")
void revoke_validId_returns204() {
    String u = UUID.randomUUID().toString().substring(0, 8);
    AuthResponse owner = doSignup("RevokeCo " + u, "revokeowner+" + u + "@example.com");

    InviteRequest req = new InviteRequest("torevoke+" + u + "@example.com", Role.MEMBER);
    ResponseEntity<ApiResponse<InvitationResponse>> invResp = restTemplate.exchange(
            "/api/v1/invitations/invite", HttpMethod.POST,
            new HttpEntity<>(req, authHeader(owner.accessToken())),
            new ParameterizedTypeReference<>() {}
    );
    UUID invId = invResp.getBody().data().id();

    ResponseEntity<Void> resp = restTemplate.exchange(
            "/api/v1/invitations/" + invId, HttpMethod.DELETE,
            new HttpEntity<>(authHeader(owner.accessToken())),
            Void.class
    );

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM invitations WHERE id = ?", Integer.class, invId);
    assertThat(count).isEqualTo(0);
}

@Test
@DisplayName("revoke: non-existent id returns 404")
void revoke_unknownId_returns404() {
    String u = UUID.randomUUID().toString().substring(0, 8);
    AuthResponse owner = doSignup("Revoke404Co " + u, "rv404+" + u + "@example.com");

    ResponseEntity<Object> resp = restTemplate.exchange(
            "/api/v1/invitations/" + UUID.randomUUID(), HttpMethod.DELETE,
            new HttpEntity<>(authHeader(owner.accessToken())),
            new ParameterizedTypeReference<>() {}
    );

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
}
```

- [ ] **Step 6.2: Run tests — expect 404 (endpoint not yet mapped)**

```bash
mvn test -Dtest=InvitationManagementIT#revoke_validId_returns204 -pl . 2>&1 | tail -10
```

Expected: FAIL.

- [ ] **Step 6.3: Add `revoke` to `InvitationService`**

```java
void revoke(UUID id, JwtClaims caller);
```

- [ ] **Step 6.4: Implement `revoke` in `InvitationServiceImpl`**

```java
@Override
@Transactional
public void revoke(UUID id, JwtClaims caller) {
    Role callerRole = Role.valueOf(caller.role());
    if (callerRole != Role.ADMIN && callerRole != Role.OWNER) {
        throw new AccessDeniedException("Only ADMIN or OWNER can revoke invitations");
    }

    Invitation invitation = invitationRepository.findById(id)
            .orElseThrow(() -> new InvitationNotFoundException(id));

    if (invitation.getAcceptedAt() != null) {
        throw new InvitationNotFoundException(id);
    }

    invitationRepository.delete(invitation);
}
```

- [ ] **Step 6.5: Add endpoint to `InvitationController`**

Add inside `InvitationController`, before the closing brace:

```java
@DeleteMapping("/{id}")
@ResponseStatus(HttpStatus.NO_CONTENT)
@SecurityRequirement(name = "Bearer")
@Operation(summary = "Revoke a pending invitation")
@ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Invitation revoked"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient role"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Invitation not found or already accepted")
})
public void revoke(@PathVariable UUID id) {
    invitationService.revoke(id, caller());
}
```

- [ ] **Step 6.6: Run all tests**

```bash
mvn test -pl . 2>&1 | tail -30
```

Expected: `BUILD SUCCESS`, all tests green.

- [ ] **Step 6.7: Commit**

```bash
git add src/main/java/com/leonardtrinh/supportsaas/invitation/ \
        src/test/java/com/leonardtrinh/supportsaas/invitation/InvitationManagementIT.java
git commit -m "feat(invitation): add DELETE /api/v1/invitations/{id} — revoke pending invite"
```

---

## Self-Review Checklist

- [x] **Spec coverage:** All 4 endpoints from spec section 11 covered (members paginated ✅, list pending ✅, resend ✅, revoke ✅)
- [x] **No placeholders:** All steps contain concrete code
- [x] **Type consistency:** `InvitationResponse` constructor `(id, email, role, expiresAt, createdAt)` matches existing record definition in all tasks
- [x] **Path conflict:** `POST /api/v1/members/invite` moves to `POST /api/v1/invitations/invite` — `MemberManagementIT` invite paths updated in Task 4 Step 4.7
- [x] **Hibernate filter:** All new service methods run within authenticated context (TenantContext set by JwtAuthFilter) — `findById`, `findAll`, `findAllPending` are all correctly scoped
- [x] **`@Transactional`:** `resend` and `revoke` annotated `@Transactional`; `listPending` inherits class-level `@Transactional(readOnly = true)` from `InvitationServiceImpl`
