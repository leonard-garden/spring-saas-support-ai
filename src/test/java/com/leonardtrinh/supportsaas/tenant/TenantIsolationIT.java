package com.leonardtrinh.supportsaas.tenant;

import com.leonardtrinh.supportsaas.auth.AuthResponse;
import com.leonardtrinh.supportsaas.common.ApiResponse;
import com.leonardtrinh.supportsaas.common.BaseIT;
import com.leonardtrinh.supportsaas.common.PagedResponse;
import com.leonardtrinh.supportsaas.invitation.Invitation;
import com.leonardtrinh.supportsaas.invitation.InvitationRepository;
import com.leonardtrinh.supportsaas.member.Member;
import com.leonardtrinh.supportsaas.member.MemberRepository;
import com.leonardtrinh.supportsaas.member.MemberResponse;
import com.leonardtrinh.supportsaas.member.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

class TenantIsolationIT extends BaseIT {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private InvitationRepository invitationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    @Qualifier("taskExecutor")
    private Executor taskExecutor;

    @BeforeEach
    void setUp() {
        configureRestTemplate();
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private void insertTestInvitation(UUID businessId, String email, Role role, Instant expiresAt) {
        String rawToken = "test-token-" + UUID.randomUUID();
        String tokenHash = sha256(rawToken);
        jdbcTemplate.update(
                "INSERT INTO invitations (business_id, email, role, token_hash, expires_at) VALUES (?, ?, ?, ?, ?)",
                businessId, email, role.name(), tokenHash, Timestamp.from(expiresAt));
    }

    // ---------------------------------------------------------------
    // Scenario 1 & 2: Member repo isolation (direct repository)
    //
    // TenantFilterAspect fires within the transaction opened by TransactionTemplate,
    // so the Hibernate session is bound before the aspect enables the filter.
    // Without this outer transaction, em.find() would use a fresh session where
    // the filter has not been applied.
    // ---------------------------------------------------------------

    @Test
    @DisplayName("memberRepo: TenantA context returns only TenantA members")
    void memberRepo_tenantAContext_returnsOnlyTenantAMembers() {
        AuthResponse tenantA = doSignup(uniqueName("BizA"), uniqueEmail("ownerA"));
        AuthResponse tenantB = doSignup(uniqueName("BizB"), uniqueEmail("ownerB"));

        List<UUID> ids = transactionTemplate.execute(status -> {
            TenantContext.setTenantId(tenantA.businessId());
            try {
                return memberRepository.findAll().stream().map(Member::getId).toList();
            } finally {
                TenantContext.clear();
            }
        });

        assertThat(ids).contains(tenantA.memberId());
        assertThat(ids).doesNotContain(tenantB.memberId());
    }

    @Test
    @DisplayName("memberRepo: TenantB context returns only TenantB members")
    void memberRepo_tenantBContext_returnsOnlyTenantBMembers() {
        AuthResponse tenantA = doSignup(uniqueName("BizA2"), uniqueEmail("ownerA2"));
        AuthResponse tenantB = doSignup(uniqueName("BizB2"), uniqueEmail("ownerB2"));

        List<UUID> ids = transactionTemplate.execute(status -> {
            TenantContext.setTenantId(tenantB.businessId());
            try {
                return memberRepository.findAll().stream().map(Member::getId).toList();
            } finally {
                TenantContext.clear();
            }
        });

        assertThat(ids).contains(tenantB.memberId());
        assertThat(ids).doesNotContain(tenantA.memberId());
    }

    // ---------------------------------------------------------------
    // Scenario 3: Invitation repo isolation (direct repository)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("invitationRepo: filter isolates invitations per tenant")
    void invitationRepo_tenantFilter_isolatesInvitations() {
        AuthResponse tenantA = doSignup(uniqueName("InvBizA"), uniqueEmail("invOwnerA"));
        AuthResponse tenantB = doSignup(uniqueName("InvBizB"), uniqueEmail("invOwnerB"));

        String emailA = uniqueEmail("inviteeA");
        String emailB = uniqueEmail("inviteeB");
        insertTestInvitation(tenantA.businessId(), emailA, Role.ADMIN, Instant.now().plus(7, ChronoUnit.DAYS));
        insertTestInvitation(tenantB.businessId(), emailB, Role.MEMBER, Instant.now().plus(7, ChronoUnit.DAYS));

        UUID invIdA = jdbcTemplate.queryForObject(
                "SELECT id FROM invitations WHERE email = ?", UUID.class, emailA);
        UUID invIdB = jdbcTemplate.queryForObject(
                "SELECT id FROM invitations WHERE email = ?", UUID.class, emailB);

        // TenantA context: sees invIdA, not invIdB
        List<UUID> idsA = transactionTemplate.execute(status -> {
            TenantContext.setTenantId(tenantA.businessId());
            try {
                return invitationRepository.findAll().stream().map(Invitation::getId).toList();
            } finally {
                TenantContext.clear();
            }
        });
        assertThat(idsA).contains(invIdA);
        assertThat(idsA).doesNotContain(invIdB);

        // TenantB context: sees invIdB, not invIdA
        List<UUID> idsB = transactionTemplate.execute(status -> {
            TenantContext.setTenantId(tenantB.businessId());
            try {
                return invitationRepository.findAll().stream().map(Invitation::getId).toList();
            } finally {
                TenantContext.clear();
            }
        });
        assertThat(idsB).contains(invIdB);
        assertThat(idsB).doesNotContain(invIdA);
    }

    // ---------------------------------------------------------------
    // Scenario 4: API-level member isolation
    // ---------------------------------------------------------------

    @Test
    @DisplayName("GET /members: API returns only caller's tenant members by specific ID")
    void membersApi_returnsOnlyCallerTenantMembersBySpecificId() {
        AuthResponse tenantA = doSignup(uniqueName("ApiBizA"), uniqueEmail("apiOwnerA"));
        AuthResponse tenantB = doSignup(uniqueName("ApiBizB"), uniqueEmail("apiOwnerB"));

        ResponseEntity<ApiResponse<PagedResponse<MemberResponse>>> respA = restTemplate.exchange(
                "/api/v1/members?page=0&size=100", HttpMethod.GET,
                new HttpEntity<>(authHeader(tenantA.accessToken())),
                new ParameterizedTypeReference<>() {}
        );
        ResponseEntity<ApiResponse<PagedResponse<MemberResponse>>> respB = restTemplate.exchange(
                "/api/v1/members?page=0&size=100", HttpMethod.GET,
                new HttpEntity<>(authHeader(tenantB.accessToken())),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(respA.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respB.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<UUID> idsA = respA.getBody().data().content().stream().map(MemberResponse::id).toList();
        List<UUID> idsB = respB.getBody().data().content().stream().map(MemberResponse::id).toList();

        assertThat(idsA).contains(tenantA.memberId());
        assertThat(idsA).doesNotContain(tenantB.memberId());

        assertThat(idsB).contains(tenantB.memberId());
        assertThat(idsB).doesNotContain(tenantA.memberId());
    }

    // ---------------------------------------------------------------
    // Scenario 5: Cross-tenant resource access returns 404 not 403
    //
    // em.find() bypasses Hibernate filters, so the service must do an explicit
    // businessId check. Returning 404 (not 403) avoids leaking resource existence.
    // ---------------------------------------------------------------

    @Test
    @DisplayName("GET /members/{id}: cross-tenant access returns 404, not 403")
    void memberGet_crossTenantAccess_returns404() {
        AuthResponse tenantA = doSignup(uniqueName("404BizA"), uniqueEmail("404OwnerA"));
        AuthResponse tenantB = doSignup(uniqueName("404BizB"), uniqueEmail("404OwnerB"));

        ResponseEntity<Void> resp = restTemplate.exchange(
                "/api/v1/members/" + tenantB.memberId(), HttpMethod.GET,
                new HttpEntity<>(authHeader(tenantA.accessToken())),
                Void.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---------------------------------------------------------------
    // Scenario 6: Async context propagation
    // ---------------------------------------------------------------

    @Test
    @DisplayName("taskExecutor: tenant context is propagated to async threads")
    void taskExecutor_propagatesTenantContextToAsyncThread() throws Exception {
        UUID someTenantId = UUID.randomUUID();

        TenantContext.setTenantId(someTenantId);
        try {
            CompletableFuture<UUID> capturedId = new CompletableFuture<>();
            taskExecutor.execute(() -> {
                try {
                    capturedId.complete(TenantContext.getTenantId());
                } catch (Exception e) {
                    capturedId.completeExceptionally(e);
                }
            });

            assertThat(capturedId.get()).isEqualTo(someTenantId);
        } finally {
            TenantContext.clear();
        }
    }

    // ---------------------------------------------------------------
    // Scenario 7: Null TenantContext — documents current behavior
    // ---------------------------------------------------------------

    @Test
    @DisplayName("memberRepo: no tenant context set — filter not applied, all members returned")
    void memberRepo_noTenantContext_filterNotApplied() {
        TenantContext.clear();

        doSignup(uniqueName("NullCtxBizA"), uniqueEmail("nullCtxOwnerA"));
        doSignup(uniqueName("NullCtxBizB"), uniqueEmail("nullCtxOwnerB"));

        // KNOWN BEHAVIOR: filter not enforced when TenantContext is null — callers must ensure context is always set
        List<Member> result = transactionTemplate.execute(status -> memberRepository.findAll());

        assertThat(result.size()).isGreaterThanOrEqualTo(2);
    }
}
