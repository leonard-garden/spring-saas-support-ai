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
}
