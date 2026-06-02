package com.leonardtrinh.supportsaas.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Adds hardening security headers to every HTTP response.
 * Mitigates XSS, clickjacking, content-sniffing, and transport-downgrade attacks.
 * Ref: SRS FR-030
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    static final String HEADER_CONTENT_SECURITY_POLICY = "Content-Security-Policy";
    static final String HEADER_STRICT_TRANSPORT_SECURITY = "Strict-Transport-Security";
    static final String HEADER_X_FRAME_OPTIONS = "X-Frame-Options";
    static final String HEADER_X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    static final String HEADER_X_XSS_PROTECTION = "X-XSS-Protection";
    static final String HEADER_REFERRER_POLICY = "Referrer-Policy";

    static final String VALUE_CSP =
            "default-src 'self'; script-src 'self' js.stripe.com; frame-src js.stripe.com";
    static final String VALUE_HSTS = "max-age=31536000; includeSubDomains";
    static final String VALUE_X_FRAME_OPTIONS = "DENY";
    static final String VALUE_X_CONTENT_TYPE_OPTIONS = "nosniff";
    static final String VALUE_X_XSS_PROTECTION = "0";
    static final String VALUE_REFERRER_POLICY = "strict-origin-when-cross-origin";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        response.setHeader(HEADER_CONTENT_SECURITY_POLICY, VALUE_CSP);
        response.setHeader(HEADER_STRICT_TRANSPORT_SECURITY, VALUE_HSTS);
        response.setHeader(HEADER_X_FRAME_OPTIONS, VALUE_X_FRAME_OPTIONS);
        response.setHeader(HEADER_X_CONTENT_TYPE_OPTIONS, VALUE_X_CONTENT_TYPE_OPTIONS);
        response.setHeader(HEADER_X_XSS_PROTECTION, VALUE_X_XSS_PROTECTION);
        response.setHeader(HEADER_REFERRER_POLICY, VALUE_REFERRER_POLICY);

        filterChain.doFilter(request, response);
    }
}
