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

    @Test
    @DisplayName("listPending: returns only pending (non-accepted, non-expired) invitations for caller's tenant")
    void listPending_returnsOnlyPendingForTenant() {
        String u = UUID.randomUUID().toString().substring(0, 8);
        AuthResponse owner = doSignup("ListCo " + u, "listowner+" + u + "@example.com");

        insertTestInvitation(owner.businessId(), "pending1+" + u + "@example.com",
                Role.MEMBER, Instant.now().plus(7, ChronoUnit.DAYS));
        insertTestInvitation(owner.businessId(), "pending2+" + u + "@example.com",
                Role.ADMIN, Instant.now().plus(7, ChronoUnit.DAYS));
        // Expired — should NOT appear
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
        assertThat(resp.getBody().data().expiresAt())
                .isAfter(Instant.now().plus(71, ChronoUnit.HOURS));
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

    @Test
    @DisplayName("resend: cross-tenant invitation id returns 404")
    void resend_crossTenantId_returns404() {
        String u1 = UUID.randomUUID().toString().substring(0, 8);
        String u2 = UUID.randomUUID().toString().substring(0, 8);
        AuthResponse tenant1 = doSignup("ResendIsoA " + u1, "riso1+" + u1 + "@example.com");
        AuthResponse tenant2 = doSignup("ResendIsoB " + u2, "riso2+" + u2 + "@example.com");

        // Create an invitation belonging to tenant1
        InviteRequest req = new InviteRequest("cross+" + u1 + "@example.com", Role.MEMBER);
        ResponseEntity<ApiResponse<InvitationResponse>> invResp = restTemplate.exchange(
                "/api/v1/invitations/invite", HttpMethod.POST,
                new HttpEntity<>(req, authHeader(tenant1.accessToken())),
                new ParameterizedTypeReference<>() {}
        );
        UUID invId = invResp.getBody().data().id();

        // Tenant2 tries to resend tenant1's invitation — should be 404
        ResponseEntity<Object> resp = restTemplate.exchange(
                "/api/v1/invitations/" + invId + "/resend", HttpMethod.POST,
                new HttpEntity<>(authHeader(tenant2.accessToken())),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

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

    @Test
    @DisplayName("revoke: cross-tenant invitation id returns 404")
    void revoke_crossTenantId_returns404() {
        String u1 = UUID.randomUUID().toString().substring(0, 8);
        String u2 = UUID.randomUUID().toString().substring(0, 8);
        AuthResponse tenant1 = doSignup("RevokeIsoA " + u1, "rviso1+" + u1 + "@example.com");
        AuthResponse tenant2 = doSignup("RevokeIsoB " + u2, "rviso2+" + u2 + "@example.com");

        // Create an invitation belonging to tenant1
        InviteRequest req = new InviteRequest("crossrevoke+" + u1 + "@example.com", Role.MEMBER);
        ResponseEntity<ApiResponse<InvitationResponse>> invResp = restTemplate.exchange(
                "/api/v1/invitations/invite", HttpMethod.POST,
                new HttpEntity<>(req, authHeader(tenant1.accessToken())),
                new ParameterizedTypeReference<>() {}
        );
        UUID invId = invResp.getBody().data().id();

        // Tenant2 tries to revoke tenant1's invitation — should be 404
        ResponseEntity<Object> resp = restTemplate.exchange(
                "/api/v1/invitations/" + invId, HttpMethod.DELETE,
                new HttpEntity<>(authHeader(tenant2.accessToken())),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Verify invitation still exists (not deleted by the cross-tenant attempt)
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM invitations WHERE id = ?", Integer.class, invId);
        assertThat(count).isEqualTo(1);
    }
}
