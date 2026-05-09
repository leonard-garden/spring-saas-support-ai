package com.leonardtrinh.supportsaas.common;

import com.leonardtrinh.supportsaas.auth.AuthResponse;
import com.leonardtrinh.supportsaas.auth.SignupRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BaseIT {

    @Autowired
    protected TestRestTemplate restTemplate;

    protected void configureRestTemplate() {
        restTemplate.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
    }

    protected AuthResponse doSignup(String businessName, String email) {
        SignupRequest req = new SignupRequest(businessName, email, "password123");
        ResponseEntity<ApiResponse<AuthResponse>> resp = restTemplate.exchange(
                "/api/v1/auth/signup",
                HttpMethod.POST,
                new HttpEntity<>(req),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isNotNull();
        return resp.getBody().data();
    }

    protected HttpHeaders authHeader(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return headers;
    }

    protected String uniqueEmail(String prefix) {
        return prefix + "+" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    protected String uniqueName(String prefix) {
        return prefix + " " + UUID.randomUUID().toString().substring(0, 8);
    }
}
