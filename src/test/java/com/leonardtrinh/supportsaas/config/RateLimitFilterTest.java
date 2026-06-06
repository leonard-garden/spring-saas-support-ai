package com.leonardtrinh.supportsaas.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private FilterChain filterChain;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(new ObjectMapper());
    }

    @Test
    @DisplayName("requests within auth limit are allowed")
    void authPath_withinLimit_passes() throws Exception {
        MockHttpServletRequest request = buildRequest("1.2.3.4", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        for (int i = 0; i < RateLimitFilter.AUTH_LIMIT; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }

        verify(filterChain, times(RateLimitFilter.AUTH_LIMIT)).doFilter(request, response);
        assertThat(response.getStatus()).isNotEqualTo(429);
    }

    @Test
    @DisplayName("auth path returns 429 after exceeding limit")
    void authPath_exceedsLimit_returns429() throws Exception {
        MockHttpServletRequest request = buildRequest("10.0.0.1", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Exhaust the bucket
        for (int i = 0; i < RateLimitFilter.AUTH_LIMIT; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }
        // One request over the limit
        MockHttpServletResponse overLimitResponse = new MockHttpServletResponse();
        filter.doFilterInternal(request, overLimitResponse, filterChain);

        assertThat(overLimitResponse.getStatus()).isEqualTo(429);
        assertThat(overLimitResponse.getHeader("Retry-After"))
                .isEqualTo(String.valueOf(RateLimitFilter.RETRY_AFTER_SECONDS));
        assertThat(overLimitResponse.getContentAsString()).contains("Rate limit exceeded");
        // filterChain was NOT called for the over-limit request
        verify(filterChain, times(RateLimitFilter.AUTH_LIMIT)).doFilter(any(), any());
    }

    @Test
    @DisplayName("widget path returns 429 after exceeding limit")
    void widgetPath_exceedsLimit_returns429() throws Exception {
        MockHttpServletRequest request = buildRequest("10.0.0.2", "/api/v1/widget/chat");
        MockHttpServletResponse response = new MockHttpServletResponse();

        for (int i = 0; i < RateLimitFilter.WIDGET_LIMIT; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }
        MockHttpServletResponse overLimitResponse = new MockHttpServletResponse();
        filter.doFilterInternal(request, overLimitResponse, filterChain);

        assertThat(overLimitResponse.getStatus()).isEqualTo(429);
        verify(filterChain, times(RateLimitFilter.WIDGET_LIMIT)).doFilter(any(), any());
    }

    @Test
    @DisplayName("different IPs have independent buckets on auth path")
    void authPath_differentIps_independentBuckets() throws Exception {
        MockHttpServletRequest reqA = buildRequest("192.168.1.1", "/api/v1/auth/login");
        MockHttpServletRequest reqB = buildRequest("192.168.1.2", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Exhaust IP A
        for (int i = 0; i < RateLimitFilter.AUTH_LIMIT; i++) {
            filter.doFilterInternal(reqA, response, filterChain);
        }
        // IP B should still pass
        MockHttpServletResponse responseB = new MockHttpServletResponse();
        filter.doFilterInternal(reqB, responseB, filterChain);

        assertThat(responseB.getStatus()).isNotEqualTo(429);
    }

    @Test
    @DisplayName("X-Forwarded-For header is used for client IP resolution")
    void resolveClientIp_usesXForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1");

        String ip = filter.resolveClientIp(request);

        assertThat(ip).isEqualTo("203.0.113.5");
    }

    @Test
    @DisplayName("falls back to remoteAddr when X-Forwarded-For is absent")
    void resolveClientIp_fallsBackToRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.16.0.99");

        String ip = filter.resolveClientIp(request);

        assertThat(ip).isEqualTo("172.16.0.99");
    }

    @Test
    @DisplayName("429 response body contains valid JSON with success=false")
    void exceededLimit_responseBodyIsValidJson() throws Exception {
        MockHttpServletRequest request = buildRequest("9.9.9.9", "/api/v1/auth/signup");
        MockHttpServletResponse response = new MockHttpServletResponse();

        for (int i = 0; i < RateLimitFilter.AUTH_LIMIT; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }
        MockHttpServletResponse overLimitResponse = new MockHttpServletResponse();
        filter.doFilterInternal(request, overLimitResponse, filterChain);

        String body = overLimitResponse.getContentAsString();
        ObjectMapper mapper = new ObjectMapper();
        var node = mapper.readTree(body);
        assertThat(node.get("success").asBoolean()).isFalse();
        assertThat(node.get("error").asText()).isEqualTo("Rate limit exceeded. Retry after 30s");
    }

    // --- helpers ---

    private MockHttpServletRequest buildRequest(String remoteAddr, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        request.setRequestURI(path);
        return request;
    }
}
