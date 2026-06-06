package com.leonardtrinh.supportsaas.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leonardtrinh.supportsaas.auth.JwtClaims;
import com.leonardtrinh.supportsaas.billing.Plan;
import com.leonardtrinh.supportsaas.billing.SubscriptionService;
import com.leonardtrinh.supportsaas.common.ApiResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tenant-based rate limiting filter using Bucket4j (in-memory, ConcurrentHashMap).
 *
 * <p>Runs AFTER {@code JwtAuthFilter} so {@code tenantId} is available from the
 * {@code SecurityContext}. Requests without an authenticated principal are skipped
 * — they are already handled by {@link RateLimitFilter} (IP-based tier).
 *
 * <p>Tier limits per tenant UUID per minute (keyed by plan slug):
 * <ul>
 *   <li>free     — 100 req/min</li>
 *   <li>starter  — 500 req/min</li>
 *   <li>pro      — 2 000 req/min</li>
 *   <li>business — unlimited (no bucket created)</li>
 * </ul>
 *
 * <p>On limit exceeded: HTTP 429, {@code Retry-After: 30} header,
 * body {@code {success:false, error:"Tenant rate limit exceeded"}}.
 */
@Component
public class TenantRateLimitFilter extends OncePerRequestFilter {

    static final int FREE_LIMIT = 100;
    static final int STARTER_LIMIT = 500;
    static final int PRO_LIMIT = 2_000;
    static final int RETRY_AFTER_SECONDS = 30;

    private static final String PLAN_SLUG_BUSINESS = "business";
    private static final String PLAN_SLUG_PRO = "pro";
    private static final String PLAN_SLUG_STARTER = "starter";
    private static final String RATE_LIMIT_EXCEEDED_MESSAGE = "Tenant rate limit exceeded";

    private final ConcurrentHashMap<UUID, Bucket> tenantBuckets = new ConcurrentHashMap<>();
    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;

    public TenantRateLimitFilter(SubscriptionService subscriptionService,
                                  ObjectMapper objectMapper) {
        this.subscriptionService = subscriptionService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        JwtClaims claims = extractClaims();
        if (claims == null) {
            // Anonymous request — IP-based RateLimitFilter handles it
            filterChain.doFilter(request, response);
            return;
        }

        UUID tenantId = claims.tenantId();
        String planSlug = resolvePlanSlug(tenantId);

        // Business plan: unlimited — short-circuit with no bucket check
        if (PLAN_SLUG_BUSINESS.equalsIgnoreCase(planSlug)) {
            filterChain.doFilter(request, response);
            return;
        }

        int limit = limitForSlug(planSlug);
        Bucket bucket = tenantBuckets.computeIfAbsent(tenantId, id -> buildBucket(limit));

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            writeTooManyRequests(response);
        }
    }

    /**
     * Returns the plan slug for the given tenant, defaulting to "free" when no
     * active subscription is found.
     */
    String resolvePlanSlug(UUID tenantId) {
        Optional<Plan> plan = subscriptionService.getCurrentPlan(tenantId);
        return plan.map(p -> p.getSlug().toLowerCase()).orElse("free");
    }

    /**
     * Maps a plan slug to its per-minute request allowance.
     * Unrecognised slugs default to the Free tier limit.
     */
    int limitForSlug(String slug) {
        return switch (slug) {
            case PLAN_SLUG_PRO -> PRO_LIMIT;
            case PLAN_SLUG_STARTER -> STARTER_LIMIT;
            default -> FREE_LIMIT; // "free" or any unknown slug
        };
    }

    private JwtClaims extractClaims() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof JwtClaims jwtClaims) {
            return jwtClaims;
        }
        return null;
    }

    private Bucket buildBucket(int requestsPerMinute) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(requestsPerMinute)
                .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(RETRY_AFTER_SECONDS));
        ApiResponse<Void> body = ApiResponse.fail(RATE_LIMIT_EXCEEDED_MESSAGE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
