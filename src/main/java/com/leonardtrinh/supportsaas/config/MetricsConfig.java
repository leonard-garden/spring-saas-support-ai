package com.leonardtrinh.supportsaas.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers application-level Micrometer counters and timers for Prometheus scraping.
 *
 * <p>Counters:
 * <ul>
 *   <li>{@code api.requests.total} — tagged by method, path, status, tenantPlan</li>
 *   <li>{@code chat.messages.total} — tagged by tenantId</li>
 *   <li>{@code quota.exceeded.total} — tagged by resource, plan</li>
 *   <li>{@code stripe.webhook.processed} — tagged by event, status</li>
 *   <li>{@code trial.expired.total}</li>
 * </ul>
 *
 * <p>Timers:
 * <ul>
 *   <li>{@code api.request.duration} — tagged by path</li>
 *   <li>{@code document.ingestion.duration} — tagged by status</li>
 * </ul>
 */
@Configuration
public class MetricsConfig {

    /**
     * Base counter for total API requests.
     * Callers must use {@code counter.tag("method", ...).tag("path", ...).tag("status", ...).tag("tenantPlan", ...)} via MeterRegistry.
     * This bean registers the metric definition; per-request recording happens at call sites.
     */
    @Bean
    public Counter apiRequestsTotalCounter(MeterRegistry registry) {
        return Counter.builder("api.requests.total")
                .description("Total number of API requests")
                .tag("method", "unknown")
                .tag("path", "unknown")
                .tag("status", "unknown")
                .tag("tenantPlan", "unknown")
                .register(registry);
    }

    /**
     * Counter for chat messages, tagged by tenantId.
     */
    @Bean
    public Counter chatMessagesTotalCounter(MeterRegistry registry) {
        return Counter.builder("chat.messages.total")
                .description("Total number of chat messages processed")
                .tag("tenantId", "unknown")
                .register(registry);
    }

    /**
     * Counter for quota exceeded events, tagged by resource and plan.
     */
    @Bean
    public Counter quotaExceededTotalCounter(MeterRegistry registry) {
        return Counter.builder("quota.exceeded.total")
                .description("Total number of quota exceeded events")
                .tag("resource", "unknown")
                .tag("plan", "unknown")
                .register(registry);
    }

    /**
     * Counter for processed Stripe webhook events, tagged by event type and status.
     */
    @Bean
    public Counter stripeWebhookProcessedCounter(MeterRegistry registry) {
        return Counter.builder("stripe.webhook.processed")
                .description("Total number of Stripe webhook events processed")
                .tag("event", "unknown")
                .tag("status", "unknown")
                .register(registry);
    }

    /**
     * Counter for expired trials.
     */
    @Bean
    public Counter trialExpiredTotalCounter(MeterRegistry registry) {
        return Counter.builder("trial.expired.total")
                .description("Total number of trials that have expired")
                .register(registry);
    }

    /**
     * Timer for API request duration, tagged by path.
     */
    @Bean
    public Timer apiRequestDurationTimer(MeterRegistry registry) {
        return Timer.builder("api.request.duration")
                .description("Duration of API requests")
                .tag("path", "unknown")
                .register(registry);
    }

    /**
     * Timer for document ingestion duration, tagged by status (success/failure).
     */
    @Bean
    public Timer documentIngestionDurationTimer(MeterRegistry registry) {
        return Timer.builder("document.ingestion.duration")
                .description("Duration of document ingestion pipeline")
                .tag("status", "unknown")
                .register(registry);
    }
}
