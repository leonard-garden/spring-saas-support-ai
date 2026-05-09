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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthIT {

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

    private ResponseEntity<Void> doLogout(String refreshToken, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return restTemplate.exchange(
                "/api/v1/auth/logout",
                HttpMethod.POST,
                new HttpEntity<>(new RefreshRequest(refreshToken), headers),
                Void.class
        );
    }

    private ResponseEntity<ApiResponse<MeResponse>> doMe(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return restTemplate.exchange(
                "/api/v1/auth/me",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
        );
    }

    // ---------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------

    @Test
    @DisplayName("signup: creates business, member, subscription — returns 201 with tokens")
    void signup_createsBusinessMemberSubscription_returns201() {
        SignupRequest req = uniqueSignup();
        ResponseEntity<ApiResponse<AuthResponse>> resp = doSignup(req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ApiResponse<AuthResponse> body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.success()).isTrue();
        assertThat(body.data().accessToken()).isNotBlank();
        assertThat(body.data().refreshToken()).isNotBlank();
        assertThat(body.data().businessId()).isNotNull();
        assertThat(body.data().memberId()).isNotNull();
    }

    @Test
    @DisplayName("signup: duplicate email returns 409")
    void signup_duplicateEmail_returns409() {
        SignupRequest req = uniqueSignup();
        doSignup(req); // first signup

        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                "/api/v1/auth/signup",
                HttpMethod.POST,
                new HttpEntity<>(req),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("login: valid credentials return 200 with tokens")
    void login_validCredentials_returns200WithTokens() {
        SignupRequest req = uniqueSignup();
        doSignup(req);

        ResponseEntity<ApiResponse<AuthResponse>> resp = doLogin(req.email(), req.password());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        ApiResponse<AuthResponse> body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.success()).isTrue();
        assertThat(body.data().accessToken()).isNotBlank();
        assertThat(body.data().refreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("login: wrong password returns 401 with generic message")
    void login_wrongPassword_returns401WithGenericMessage() {
        SignupRequest req = uniqueSignup();
        doSignup(req);

        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(new LoginRequest(req.email(), "wrongpassword")),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // must not leak any user-specific info
        String detail = (String) resp.getBody().get("detail");
        assertThat(detail).isEqualTo("Invalid credentials");
    }

    @Test
    @DisplayName("refresh: valid token returns new tokens")
    void refresh_validToken_returnsNewTokens() {
        SignupRequest req = uniqueSignup();
        ApiResponse<AuthResponse> signup = doSignup(req).getBody();
        String originalRefresh = signup.data().refreshToken();

        ResponseEntity<ApiResponse<AuthResponse>> resp = doRefresh(originalRefresh);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        ApiResponse<AuthResponse> body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.data().accessToken()).isNotBlank();
        assertThat(body.data().refreshToken()).isNotBlank();
        assertThat(body.data().refreshToken()).isNotEqualTo(originalRefresh);
    }

    @Test
    @DisplayName("refresh: revoked token returns 401")
    void refresh_revokedToken_returns401() {
        SignupRequest req = uniqueSignup();
        ApiResponse<AuthResponse> signup = doSignup(req).getBody();
        String refreshToken = signup.data().refreshToken();

        // rotate once — old token is now revoked
        doRefresh(refreshToken);

        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                "/api/v1/auth/refresh",
                HttpMethod.POST,
                new HttpEntity<>(new RefreshRequest(refreshToken)),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("logout: revokes token — subsequent refresh returns 401")
    void logout_revokesToken_subsequentRefreshReturns401() {
        SignupRequest req = uniqueSignup();
        ApiResponse<AuthResponse> signup = doSignup(req).getBody();
        String refreshToken = signup.data().refreshToken();
        String accessToken = signup.data().accessToken();

        ResponseEntity<Void> logoutResp = doLogout(refreshToken, accessToken);
        assertThat(logoutResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map<String, Object>> refreshResp = restTemplate.exchange(
                "/api/v1/auth/refresh",
                HttpMethod.POST,
                new HttpEntity<>(new RefreshRequest(refreshToken)),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(refreshResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("me: returns correct user and business info")
    void me_returnsCorrectInfo() {
        SignupRequest req = uniqueSignup();
        ApiResponse<AuthResponse> signup = doSignup(req).getBody();
        String accessToken = signup.data().accessToken();

        ResponseEntity<ApiResponse<MeResponse>> resp = doMe(accessToken);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        ApiResponse<MeResponse> body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.data().email()).isEqualTo(req.email());
        assertThat(body.data().role()).isEqualTo("OWNER");
        assertThat(body.data().businessId()).isEqualTo(signup.data().businessId());
        assertThat(body.data().businessName()).isNotBlank();
    }

    @Test
    @DisplayName("login: suspended business returns 403")
    void login_suspendedBusiness_returns403() {
        SignupRequest req = uniqueSignup();
        doSignup(req);

        jdbcTemplate.update(
                "UPDATE businesses SET suspended_at = now() WHERE id = (SELECT business_id FROM members WHERE email = ?)",
                req.email());

        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(new LoginRequest(req.email(), req.password())),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
