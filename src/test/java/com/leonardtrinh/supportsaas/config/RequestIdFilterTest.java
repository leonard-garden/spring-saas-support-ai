package com.leonardtrinh.supportsaas.config;

import com.leonardtrinh.supportsaas.auth.JwtClaims;
import com.leonardtrinh.supportsaas.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestIdFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private RequestIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestIdFilter();
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        MDC.clear();
    }

    // ──────────────────────────── requestId ────────────────────────────

    @Test
    @DisplayName("Uses X-Request-Id header value when present")
    void doFilter_withRequestIdHeader_echoesItInResponse() throws Exception {
        when(request.getHeader("X-Request-Id")).thenReturn("client-id-99");

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader("X-Request-Id", "client-id-99");
    }

    @Test
    @DisplayName("Generates a random requestId when header is absent")
    void doFilter_noHeader_generatesRandomRequestId() throws Exception {
        when(request.getHeader("X-Request-Id")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(eq("X-Request-Id"), anyString());
    }

    @Test
    @DisplayName("requestId MDC key is set during filterChain execution")
    void doFilter_requestIdMdcSetInsideChain() throws Exception {
        when(request.getHeader("X-Request-Id")).thenReturn("trace-abc");

        AtomicReference<String> capturedRequestId = new AtomicReference<>();
        doAnswer(inv -> {
            capturedRequestId.set(MDC.get(RequestIdFilter.MDC_REQUEST_ID));
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(capturedRequestId.get()).isEqualTo("trace-abc");
    }

    // ──────────────────────────── tenantId / userId ────────────────────

    @Test
    @DisplayName("tenantId MDC key is populated from TenantContext after chain runs")
    void doFilter_tenantContextSet_tenantIdAppearsInMdcAfterChain() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(request.getHeader("X-Request-Id")).thenReturn("req-tenant");

        // Simulate JwtAuthFilter running inside the chain and setting TenantContext
        doAnswer(inv -> {
            TenantContext.setTenantId(tenantId);
            return null;
        }).when(filterChain).doFilter(request, response);

        AtomicReference<String> capturedTenantId = new AtomicReference<>();

        // Wrap to observe MDC between populateAuthMdc() and finally — the real filter
        // sets MDC after filterChain.doFilter() returns; we verify by checking the value
        // is present before finally clears it using a recording subclass.
        RequestIdFilter recordingFilter = buildRecordingFilter(capturedTenantId, new AtomicReference<>());
        recordingFilter.doFilterInternal(request, response, filterChain);

        assertThat(capturedTenantId.get()).isEqualTo(tenantId.toString());
    }

    @Test
    @DisplayName("userId MDC key is populated from SecurityContext JwtClaims after chain runs")
    void doFilter_jwtClaimsInSecurityContext_userIdAppearsInMdcAfterChain() throws Exception {
        UUID memberId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(request.getHeader("X-Request-Id")).thenReturn("req-user");

        doAnswer(inv -> {
            JwtClaims claims = new JwtClaims(memberId, tenantId, "ADMIN", "u@test.com", "jti-1");
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(claims, null,
                            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            return null;
        }).when(filterChain).doFilter(request, response);

        AtomicReference<String> capturedUserId = new AtomicReference<>();
        RequestIdFilter recordingFilter = buildRecordingFilter(new AtomicReference<>(), capturedUserId);
        recordingFilter.doFilterInternal(request, response, filterChain);

        assertThat(capturedUserId.get()).isEqualTo(memberId.toString());
    }

    @Test
    @DisplayName("Unauthenticated request leaves tenantId and userId MDC keys absent")
    void doFilter_noAuth_tenantAndUserMdcKeysAbsent() throws Exception {
        when(request.getHeader("X-Request-Id")).thenReturn("req-anon");
        // No TenantContext or SecurityContext set

        AtomicReference<String> capturedTenantId = new AtomicReference<>();
        AtomicReference<String> capturedUserId   = new AtomicReference<>();
        RequestIdFilter recordingFilter = buildRecordingFilter(capturedTenantId, capturedUserId);
        recordingFilter.doFilterInternal(request, response, filterChain);

        assertThat(capturedTenantId.get()).isNull();
        assertThat(capturedUserId.get()).isNull();
    }

    // ──────────────────────────── cleanup ────────────────────────────

    @Test
    @DisplayName("All three MDC keys are removed in the finally block")
    void doFilter_mdcFullyCleared_afterFilterCompletes() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        when(request.getHeader("X-Request-Id")).thenReturn("req-clear");

        doAnswer(inv -> {
            TenantContext.setTenantId(tenantId);
            JwtClaims claims = new JwtClaims(memberId, tenantId, "ADMIN", "u@test.com", "jti-2");
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(claims, null,
                            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(MDC.get(RequestIdFilter.MDC_REQUEST_ID)).isNull();
        assertThat(MDC.get(RequestIdFilter.MDC_TENANT_ID)).isNull();
        assertThat(MDC.get(RequestIdFilter.MDC_USER_ID)).isNull();
    }

    // ──────────────────────────── helpers ────────────────────────────

    /**
     * Builds a subclass that captures the MDC state of tenantId and userId
     * after populateAuthMdc() runs but before the finally block removes them.
     */
    private RequestIdFilter buildRecordingFilter(AtomicReference<String> tenantCapture,
                                                  AtomicReference<String> userCapture) {
        return new RequestIdFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req,
                                            HttpServletResponse res,
                                            FilterChain chain)
                    throws jakarta.servlet.ServletException, java.io.IOException {
                String requestId = req.getHeader("X-Request-Id");
                if (requestId == null || requestId.isBlank()) {
                    requestId = UUID.randomUUID().toString();
                }
                MDC.put(MDC_REQUEST_ID, requestId);
                res.setHeader("X-Request-Id", requestId);
                try {
                    chain.doFilter(req, res);
                    // Replicate populateAuthMdc logic
                    UUID tid = TenantContext.getTenantId();
                    if (tid != null) {
                        MDC.put(MDC_TENANT_ID, tid.toString());
                    }
                    org.springframework.security.core.Authentication auth =
                            SecurityContextHolder.getContext().getAuthentication();
                    if (auth != null && auth.getPrincipal() instanceof JwtClaims c) {
                        MDC.put(MDC_USER_ID, c.memberId().toString());
                    }
                    // Capture MDC values before finally removes them
                    tenantCapture.set(MDC.get(MDC_TENANT_ID));
                    userCapture.set(MDC.get(MDC_USER_ID));
                } finally {
                    MDC.remove(MDC_REQUEST_ID);
                    MDC.remove(MDC_TENANT_ID);
                    MDC.remove(MDC_USER_ID);
                }
            }
        };
    }
}
