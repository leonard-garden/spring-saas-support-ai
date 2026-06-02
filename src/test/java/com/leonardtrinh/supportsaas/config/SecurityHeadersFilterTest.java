package com.leonardtrinh.supportsaas.config;

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

import static com.leonardtrinh.supportsaas.config.SecurityHeadersFilter.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SecurityHeadersFilterTest {

    @Mock
    private FilterChain filterChain;

    private SecurityHeadersFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new SecurityHeadersFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("All 6 security headers are set on every response")
    void doFilterInternal_setsAllSixSecurityHeaders() throws Exception {
        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getHeader(HEADER_CONTENT_SECURITY_POLICY)).isEqualTo(VALUE_CSP);
        assertThat(response.getHeader(HEADER_STRICT_TRANSPORT_SECURITY)).isEqualTo(VALUE_HSTS);
        assertThat(response.getHeader(HEADER_X_FRAME_OPTIONS)).isEqualTo(VALUE_X_FRAME_OPTIONS);
        assertThat(response.getHeader(HEADER_X_CONTENT_TYPE_OPTIONS)).isEqualTo(VALUE_X_CONTENT_TYPE_OPTIONS);
        assertThat(response.getHeader(HEADER_X_XSS_PROTECTION)).isEqualTo(VALUE_X_XSS_PROTECTION);
        assertThat(response.getHeader(HEADER_REFERRER_POLICY)).isEqualTo(VALUE_REFERRER_POLICY);
    }

    @Test
    @DisplayName("Filter chain is always invoked after headers are set")
    void doFilterInternal_alwaysCallsFilterChain() throws Exception {
        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("CSP allows self and Stripe script/frame sources")
    void doFilterInternal_cspAllowsStripeScriptAndFrame() throws Exception {
        filter.doFilterInternal(request, response, filterChain);

        String csp = response.getHeader(HEADER_CONTENT_SECURITY_POLICY);
        assertThat(csp).contains("script-src 'self' js.stripe.com");
        assertThat(csp).contains("frame-src js.stripe.com");
    }

    @Test
    @DisplayName("HSTS header includes max-age and includeSubDomains")
    void doFilterInternal_hstsIncludesSubDomains() throws Exception {
        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getHeader(HEADER_STRICT_TRANSPORT_SECURITY))
                .contains("max-age=31536000")
                .contains("includeSubDomains");
    }

    @Test
    @DisplayName("X-XSS-Protection is set to 0 (modern browser guidance)")
    void doFilterInternal_xXssProtectionIsZero() throws Exception {
        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getHeader(HEADER_X_XSS_PROTECTION)).isEqualTo("0");
    }
}
