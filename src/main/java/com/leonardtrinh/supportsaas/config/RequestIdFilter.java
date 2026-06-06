package com.leonardtrinh.supportsaas.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.leonardtrinh.supportsaas.auth.JwtClaims;
import com.leonardtrinh.supportsaas.tenant.TenantContext;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that sets structured MDC fields for every request:
 * <ul>
 *   <li>{@code requestId} — taken from X-Request-Id header or randomly generated</li>
 *   <li>{@code tenantId}  — populated after JWT validation via TenantContext</li>
 *   <li>{@code userId}    — populated after JWT validation via SecurityContext principal</li>
 * </ul>
 *
 * All three keys are removed in the {@code finally} block to prevent MDC leakage
 * across thread-pool reuse.
 */
@Component
public class RequestIdFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    static final String MDC_REQUEST_ID = "requestId";
    static final String MDC_TENANT_ID  = "tenantId";
    static final String MDC_USER_ID    = "userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);

            // JwtAuthFilter runs inside filterChain.doFilter — populate MDC
            // after the security filter chain has set TenantContext + SecurityContext.
            populateAuthMdc();
        } finally {
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_TENANT_ID);
            MDC.remove(MDC_USER_ID);
        }
    }

    /**
     * Reads tenantId from TenantContext and userId from the SecurityContext principal
     * (set by JwtAuthFilter). Both values are optional — unauthenticated requests will
     * simply have no tenantId/userId in their log lines.
     */
    private void populateAuthMdc() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            MDC.put(MDC_TENANT_ID, tenantId.toString());
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof JwtClaims claims) {
            MDC.put(MDC_USER_ID, claims.memberId().toString());
        }
    }
}
