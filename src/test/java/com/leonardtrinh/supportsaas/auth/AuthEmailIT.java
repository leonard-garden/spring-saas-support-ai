package com.leonardtrinh.supportsaas.auth;

import com.leonardtrinh.supportsaas.common.ApiResponse;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthEmailIT {

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

    private SignupRequest uniqueSignup() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return new SignupRequest("Acme Corp " + unique, "user+" + unique + "@example.com", "password123");
    }

    private ResponseEntity<ApiResponse<AuthResponse>> doSignup(SignupRequest req) {
        return restTemplate.exchange(
                "/api/v1/auth/signup",
                HttpMethod.POST,
                new HttpEntity<>(req),
                new ParameterizedTypeReference<>() {}
        );
    }

    private ResponseEntity<ApiResponse<AuthResponse>> doLogin(String email, String password) {
        return restTemplate.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(new LoginRequest(email, password)),
                new ParameterizedTypeReference<>() {}
        );
    }

    private ResponseEntity<ApiResponse<AuthResponse>> doRefresh(String refreshToken) {
        return restTemplate.exchange(
                "/api/v1/auth/refresh",
                HttpMethod.POST,
                new HttpEntity<>(new RefreshRequest(refreshToken)),
                new ParameterizedTypeReference<>() {}
        );
    }

    private ResponseEntity<Map<String, Object>> doForgotPassword(String email) {
        return restTemplate.exchange(
                "/api/v1/auth/forgot-password",
                HttpMethod.POST,
                new HttpEntity<>(new ForgotPasswordRequest(email)),
                new ParameterizedTypeReference<>() {}
        );
    }

    private ResponseEntity<Void> doResetPassword(String token, String newPassword) {
        return restTemplate.exchange(
                "/api/v1/auth/reset-password",
                HttpMethod.POST,
                new HttpEntity<>(new ResetPasswordRequest(token, newPassword)),
                Void.class
        );
    }

    private ResponseEntity<Void> doVerifyEmail(String token) {
        return restTemplate.exchange(
                "/api/v1/auth/verify-email",
                HttpMethod.POST,
                new HttpEntity<>(new VerifyEmailRequest(token)),
                Void.class
        );
    }

    private UUID getMemberId(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE email = ?",
                UUID.class, email);
    }

    private String insertResetToken(UUID memberId, Instant expiresAt) {
        String plaintext = "test-reset-token-" + UUID.randomUUID();
        String hash = JwtServiceImpl.sha256Hex(plaintext);
        jdbcTemplate.update(
                "INSERT INTO password_reset_tokens (member_id, token_hash, expires_at) VALUES (?, ?, ?)",
                memberId, hash, Timestamp.from(expiresAt));
        return plaintext;
    }

    private String insertVerificationToken(UUID memberId, Instant expiresAt) {
        String plaintext = "test-verify-token-" + UUID.randomUUID();
        String hash = JwtServiceImpl.sha256Hex(plaintext);
        jdbcTemplate.update(
                "INSERT INTO email_verification_tokens (member_id, token_hash, expires_at) VALUES (?, ?, ?)",
                memberId, hash, Timestamp.from(expiresAt));
        return plaintext;
    }

    // ---------------------------------------------------------------
    // Tests — forgot password
    // ---------------------------------------------------------------

    @Test
    @DisplayName("forgotPassword: unknown email returns 200 (no user enumeration)")
    void forgotPassword_unknownEmail_returns200() {
        ResponseEntity<Map<String, Object>> resp = doForgotPassword("nobody@example.com");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("forgotPassword: known email returns 200 and creates token row in DB")
    void forgotPassword_knownEmail_returns200AndCreatesToken() {
        SignupRequest req = uniqueSignup();
        doSignup(req);

        ResponseEntity<Map<String, Object>> resp = doForgotPassword(req.email());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM password_reset_tokens WHERE member_id = (SELECT id FROM members WHERE email = ?)",
                req.email());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("used_at")).isNull();
    }

    // ---------------------------------------------------------------
    // Tests — reset password
    // ---------------------------------------------------------------

    @Test
    @DisplayName("resetPassword: valid token returns 204 and password is changed")
    void resetPassword_validToken_returns204AndPasswordChanged() {
        SignupRequest req = uniqueSignup();
        doSignup(req);
        UUID memberId = getMemberId(req.email());

        String resetToken = insertResetToken(memberId, Instant.now().plus(1, ChronoUnit.HOURS));

        ResponseEntity<Void> resp = doResetPassword(resetToken, "newpassword123");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // old password no longer works
        ResponseEntity<Map<String, Object>> loginOld = restTemplate.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(new LoginRequest(req.email(), req.password())),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(loginOld.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // new password works
        ResponseEntity<ApiResponse<AuthResponse>> loginNew = doLogin(req.email(), "newpassword123");
        assertThat(loginNew.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("resetPassword: valid token revokes all existing refresh tokens")
    void resetPassword_validToken_revokesAllRefreshTokens() {
        SignupRequest req = uniqueSignup();
        ApiResponse<AuthResponse> signup = doSignup(req).getBody();
        String oldRefreshToken = signup.data().refreshToken();
        UUID memberId = getMemberId(req.email());

        String resetToken = insertResetToken(memberId, Instant.now().plus(1, ChronoUnit.HOURS));
        doResetPassword(resetToken, "newpassword123");

        // old refresh token should now be revoked
        ResponseEntity<Map<String, Object>> refreshResp = restTemplate.exchange(
                "/api/v1/auth/refresh",
                HttpMethod.POST,
                new HttpEntity<>(new RefreshRequest(oldRefreshToken)),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(refreshResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("resetPassword: expired token returns 400")
    void resetPassword_expiredToken_returns400() {
        SignupRequest req = uniqueSignup();
        doSignup(req);
        UUID memberId = getMemberId(req.email());

        String resetToken = insertResetToken(memberId, Instant.now().minus(1, ChronoUnit.HOURS));

        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                "/api/v1/auth/reset-password",
                HttpMethod.POST,
                new HttpEntity<>(new ResetPasswordRequest(resetToken, "newpassword123")),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("resetPassword: used token returns 400 on second use")
    void resetPassword_usedToken_returns400() {
        SignupRequest req = uniqueSignup();
        doSignup(req);
        UUID memberId = getMemberId(req.email());

        String resetToken = insertResetToken(memberId, Instant.now().plus(1, ChronoUnit.HOURS));

        // first use succeeds
        ResponseEntity<Void> first = doResetPassword(resetToken, "newpassword123");
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // second use fails
        ResponseEntity<Map<String, Object>> second = restTemplate.exchange(
                "/api/v1/auth/reset-password",
                HttpMethod.POST,
                new HttpEntity<>(new ResetPasswordRequest(resetToken, "anotherpassword1")),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---------------------------------------------------------------
    // Tests — verify email
    // ---------------------------------------------------------------

    @Test
    @DisplayName("verifyEmail: valid token returns 204 and sets emailVerified = true")
    void verifyEmail_validToken_returns204AndSetsEmailVerified() {
        SignupRequest req = uniqueSignup();
        doSignup(req);
        UUID memberId = getMemberId(req.email());

        String verifyToken = insertVerificationToken(memberId, Instant.now().plus(24, ChronoUnit.HOURS));

        ResponseEntity<Void> resp = doVerifyEmail(verifyToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        Boolean emailVerified = jdbcTemplate.queryForObject(
                "SELECT email_verified FROM members WHERE id = ?",
                Boolean.class, memberId);
        assertThat(emailVerified).isTrue();
    }

    @Test
    @DisplayName("verifyEmail: expired token returns 400")
    void verifyEmail_expiredToken_returns400() {
        SignupRequest req = uniqueSignup();
        doSignup(req);
        UUID memberId = getMemberId(req.email());

        String verifyToken = insertVerificationToken(memberId, Instant.now().minus(1, ChronoUnit.HOURS));

        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                "/api/v1/auth/verify-email",
                HttpMethod.POST,
                new HttpEntity<>(new VerifyEmailRequest(verifyToken)),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("verifyEmail: used token returns 400 on second use")
    void verifyEmail_usedToken_returns400() {
        SignupRequest req = uniqueSignup();
        doSignup(req);
        UUID memberId = getMemberId(req.email());

        String verifyToken = insertVerificationToken(memberId, Instant.now().plus(24, ChronoUnit.HOURS));

        // first use succeeds
        ResponseEntity<Void> first = doVerifyEmail(verifyToken);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // second use fails
        ResponseEntity<Map<String, Object>> second = restTemplate.exchange(
                "/api/v1/auth/verify-email",
                HttpMethod.POST,
                new HttpEntity<>(new VerifyEmailRequest(verifyToken)),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
