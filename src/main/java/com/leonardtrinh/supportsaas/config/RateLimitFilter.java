package com.leonardtrinh.supportsaas.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leonardtrinh.supportsaas.common.ApiResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP-based rate limiting filter using Bucket4j (in-memory, ConcurrentHashMap).
 *
 * <p>Tier limits per IP per minute:
 * <ul>
 *   <li>/api/v1/auth/** — 20 req/min (brute-force protection)</li>
 *   <li>/api/v1/widget/** — 60 req/min (widget traffic)</li>
 *   <li>all others — 200 req/min (general API)</li>
 * </ul>
 *
 * <p>On limit exceeded: HTTP 429, {@code Retry-After: 30} header,
 * body {@code {success:false, error:"Rate limit exceeded. Retry after 30s"}}.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    static final int AUTH_LIMIT = 20;
    static final int WIDGET_LIMIT = 60;
    static final int DEFAULT_LIMIT = 200;
    static final int RETRY_AFTER_SECONDS = 30;

    private static final String RATE_LIMIT_EXCEEDED_MESSAGE = "Rate limit exceeded. Retry after 30s";
    private static final String AUTH_PATH_PREFIX = "/api/v1/auth/";
    private static final String WIDGET_PATH_PREFIX = "/api/v1/widget/";

    private final ConcurrentHashMap<String, Bucket> authBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> widgetBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> defaultBuckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public RateLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String clientIp = resolveClientIp(request);
        String path = request.getRequestURI();
        Bucket bucket = resolveBucket(clientIp, path);

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            writeTooManyRequests(response);
        }
    }

    String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For may contain a chain; take the first (original client)
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    Bucket resolveBucket(String clientIp, String path) {
        if (path.startsWith(AUTH_PATH_PREFIX)) {
            return authBuckets.computeIfAbsent(clientIp, ip -> buildBucket(AUTH_LIMIT));
        } else if (path.startsWith(WIDGET_PATH_PREFIX)) {
            return widgetBuckets.computeIfAbsent(clientIp, ip -> buildBucket(WIDGET_LIMIT));
        } else {
            return defaultBuckets.computeIfAbsent(clientIp, ip -> buildBucket(DEFAULT_LIMIT));
        }
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
