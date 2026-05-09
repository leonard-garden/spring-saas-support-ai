package com.leonardtrinh.supportsaas.member;

import com.leonardtrinh.supportsaas.auth.AuthResponse;
import com.leonardtrinh.supportsaas.auth.SignupRequest;
import com.leonardtrinh.supportsaas.common.ApiResponse;
import com.leonardtrinh.supportsaas.invitation.AcceptInvitationRequest;
import com.leonardtrinh.supportsaas.invitation.InviteRequest;
import com.leonardtrinh.supportsaas.invitation.InvitationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MemberManagementIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void configureHttpClient() {
        restTemplate.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private AuthResponse doSignup(String businessName, String email) {
        SignupRequest req = new SignupRequest(businessName, email, "password123");
        ResponseEntity<ApiResponse<AuthResponse>> resp = restTemplate.exchange(
                "/api/v1/auth/signup",
                HttpMethod.POST,
                new HttpEntity<>(req),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody().data();
    }

    private HttpHeaders authHeader(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return headers;
    }

    /**
     * Insert an invitation directly into the DB with a known raw token and return that raw token.
     */
    private String insertTestInvitation(UUID businessId, String email, Role role, Instant expiresAt) {
        String rawToken = "test-token-" + UUID.randomUUID();
        String tokenHash = sha256(rawToken);
        jdbcTemplate.update(
                "INSERT INTO invitations (business_id, email, role, token_hash, expires_at) VALUES (?, ?, ?, ?, ?)",
                businessId, email, role.name(), tokenHash, Timestamp.from(expiresAt));
        return rawToken;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // ---------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------

    @Test
    @DisplayName("invite: valid request returns 202 and creates invitation row")
    void invite_validRequest_returns202() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        AuthResponse owner = doSignup("InviteCo " + unique, "owner+" + unique + "@example.com");

        InviteRequest req = new InviteRequest("invitee+" + unique + "@example.com", Role.ADMIN);
        ResponseEntity<ApiResponse<InvitationResponse>> resp = restTemplate.exchange(
                "/api/v1/members/invite",
                HttpMethod.POST,
                new HttpEntity<>(req, authHeader(owner.accessToken())),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(resp.getBody().data().email()).isEqualTo(req.email());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM invitations WHERE email = ?", Integer.class, req.email());
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("invite: email already a member returns 409")
    void invite_emailAlreadyMember_returns409() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        AuthResponse owner = doSignup("MemberCo " + unique, "owner2+" + unique + "@example.com");

        // Try to invite the owner's own email (already a member)
        InviteRequest req = new InviteRequest("owner2+" + unique + "@example.com", Role.ADMIN);
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                "/api/v1/members/invite",
                HttpMethod.POST,
                new HttpEntity<>(req, authHeader(owner.accessToken())),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("invite: pending invitation for same email returns 409")
    void invite_emailAlreadyPending_returns409() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        AuthResponse owner = doSignup("PendingCo " + unique, "owner3+" + unique + "@example.com");

        InviteRequest req = new InviteRequest("pending+" + unique + "@example.com", Role.MEMBER);

        // First invite succeeds
        ResponseEntity<ApiResponse<InvitationResponse>> first = restTemplate.exchange(
                "/api/v1/members/invite",
                HttpMethod.POST,
                new HttpEntity<>(req, authHeader(owner.accessToken())),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // Second invite returns 409
        ResponseEntity<Map<String, Object>> second = restTemplate.exchange(
                "/api/v1/members/invite",
                HttpMethod.POST,
                new HttpEntity<>(req, authHeader(owner.accessToken())),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("invite: non-admin member returns 403")
    void invite_asNonAdmin_returns403() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        AuthResponse owner = doSignup("RoleCo " + unique, "owner4+" + unique + "@example.com");

        // Insert a MEMBER-role invitation and accept it to create a MEMBER user
        String memberEmail = "member+" + unique + "@example.com";
        String rawToken = insertTestInvitation(owner.businessId(), memberEmail, Role.MEMBER,
                Instant.now().plus(7, ChronoUnit.DAYS));

        AcceptInvitationRequest acceptReq = new AcceptInvitationRequest(rawToken, "password123");
        ResponseEntity<ApiResponse<AuthResponse>> acceptResp = restTemplate.exchange(
                "/api/v1/invitations/accept",
                HttpMethod.POST,
                new HttpEntity<>(acceptReq),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(acceptResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String memberToken = acceptResp.getBody().data().accessToken();

        // Member tries to invite → 403
        InviteRequest req = new InviteRequest("another+" + unique + "@example.com", Role.MEMBER);
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                "/api/v1/members/invite",
                HttpMethod.POST,
                new HttpEntity<>(req, authHeader(memberToken)),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("accept: valid token creates member and returns tokens")
    void accept_validToken_createsMemberAndReturnsTokens() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        AuthResponse owner = doSignup("AcceptCo " + unique, "owner5+" + unique + "@example.com");

        String inviteeEmail = "invitee+" + unique + "@example.com";
        String rawToken = insertTestInvitation(owner.businessId(), inviteeEmail, Role.ADMIN,
                Instant.now().plus(7, ChronoUnit.DAYS));

        AcceptInvitationRequest req = new AcceptInvitationRequest(rawToken, "password123");
        ResponseEntity<ApiResponse<AuthResponse>> resp = restTemplate.exchange(
                "/api/v1/invitations/accept",
                HttpMethod.POST,
                new HttpEntity<>(req),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().data().accessToken()).isNotBlank();
        assertThat(resp.getBody().data().refreshToken()).isNotBlank();
        assertThat(resp.getBody().data().businessId()).isEqualTo(owner.businessId());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM members WHERE email = ?", Integer.class, inviteeEmail);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("accept: expired token returns 400")
    void accept_expiredToken_returns400() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        AuthResponse owner = doSignup("ExpiredCo " + unique, "owner6+" + unique + "@example.com");

        String inviteeEmail = "expired+" + unique + "@example.com";
        String rawToken = insertTestInvitation(owner.businessId(), inviteeEmail, Role.MEMBER,
                Instant.now().minus(1, ChronoUnit.DAYS));

        AcceptInvitationRequest req = new AcceptInvitationRequest(rawToken, "password123");
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                "/api/v1/invitations/accept",
                HttpMethod.POST,
                new HttpEntity<>(req),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("accept: already accepted token returns 400")
    void accept_alreadyAccepted_returns400() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        AuthResponse owner = doSignup("DoubleCo " + unique, "owner7+" + unique + "@example.com");

        String inviteeEmail = "double+" + unique + "@example.com";
        String rawToken = insertTestInvitation(owner.businessId(), inviteeEmail, Role.MEMBER,
                Instant.now().plus(7, ChronoUnit.DAYS));

        AcceptInvitationRequest req = new AcceptInvitationRequest(rawToken, "password123");

        // First accept succeeds
        ResponseEntity<ApiResponse<AuthResponse>> first = restTemplate.exchange(
                "/api/v1/invitations/accept",
                HttpMethod.POST,
                new HttpEntity<>(req),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Second accept returns 410
        ResponseEntity<Map<String, Object>> second = restTemplate.exchange(
                "/api/v1/invitations/accept",
                HttpMethod.POST,
                new HttpEntity<>(req),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("listMembers: returns only members of the caller's tenant")
    void listMembers_returnsOnlyTenantMembers() {
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

        // Tenant1 sees 2 members, tenant2 sees 1
        ResponseEntity<ApiResponse<List<MemberResponse>>> resp1 = restTemplate.exchange(
                "/api/v1/members", HttpMethod.GET,
                new HttpEntity<>(authHeader(tenant1.accessToken())),
                new ParameterizedTypeReference<>() {}
        );
        ResponseEntity<ApiResponse<List<MemberResponse>>> resp2 = restTemplate.exchange(
                "/api/v1/members", HttpMethod.GET,
                new HttpEntity<>(authHeader(tenant2.accessToken())),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(resp1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp1.getBody().data()).hasSize(2);
        assertThat(resp2.getBody().data()).hasSize(1);
    }

    @Test
    @DisplayName("deleteMember: revokes refresh tokens for deleted member")
    void deleteMember_revokeRefreshTokens() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        AuthResponse owner = doSignup("DeleteCo " + unique, "owner8+" + unique + "@example.com");

        // Invite and accept a member
        String memberEmail = "todelete+" + unique + "@example.com";
        String rawToken = insertTestInvitation(owner.businessId(), memberEmail, Role.MEMBER,
                Instant.now().plus(7, ChronoUnit.DAYS));
        ResponseEntity<ApiResponse<AuthResponse>> acceptResp = restTemplate.exchange(
                "/api/v1/invitations/accept", HttpMethod.POST,
                new HttpEntity<>(new AcceptInvitationRequest(rawToken, "password123")),
                new ParameterizedTypeReference<>() {}
        );
        UUID memberId = acceptResp.getBody().data().memberId();

        // Delete the member
        ResponseEntity<Void> deleteResp = restTemplate.exchange(
                "/api/v1/members/" + memberId, HttpMethod.DELETE,
                new HttpEntity<>(authHeader(owner.accessToken())),
                Void.class
        );
        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify member is gone
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM members WHERE id = ?", Integer.class, memberId);
        assertThat(count).isEqualTo(0);

        // Verify refresh tokens revoked
        Integer activeTokens = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE member_id = ? AND revoked_at IS NULL",
                Integer.class, memberId);
        assertThat(activeTokens).isEqualTo(0);
    }

    @Test
    @DisplayName("deleteMember: self-delete returns 403")
    void deleteMember_self_returns403() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        AuthResponse owner = doSignup("SelfCo " + unique, "owner9+" + unique + "@example.com");

        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                "/api/v1/members/" + owner.memberId(), HttpMethod.DELETE,
                new HttpEntity<>(authHeader(owner.accessToken())),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("changeRole: promoting to OWNER returns 400")
    void changeRole_toOwner_returns400() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        AuthResponse owner = doSignup("PromoteCo " + unique, "owner10+" + unique + "@example.com");

        String memberEmail = "promote+" + unique + "@example.com";
        String rawToken = insertTestInvitation(owner.businessId(), memberEmail, Role.MEMBER,
                Instant.now().plus(7, ChronoUnit.DAYS));
        ResponseEntity<ApiResponse<AuthResponse>> acceptResp = restTemplate.exchange(
                "/api/v1/invitations/accept", HttpMethod.POST,
                new HttpEntity<>(new AcceptInvitationRequest(rawToken, "password123")),
                new ParameterizedTypeReference<>() {}
        );
        UUID memberId = acceptResp.getBody().data().memberId();

        UpdateRoleRequest req = new UpdateRoleRequest(Role.OWNER);
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                "/api/v1/members/" + memberId + "/role", HttpMethod.PATCH,
                new HttpEntity<>(req, authHeader(owner.accessToken())),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("changeRole: self role change returns 403")
    void changeRole_self_returns403() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        AuthResponse owner = doSignup("SelfRoleCo " + unique, "owner11+" + unique + "@example.com");

        UpdateRoleRequest req = new UpdateRoleRequest(Role.ADMIN);
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                "/api/v1/members/" + owner.memberId() + "/role", HttpMethod.PATCH,
                new HttpEntity<>(req, authHeader(owner.accessToken())),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
