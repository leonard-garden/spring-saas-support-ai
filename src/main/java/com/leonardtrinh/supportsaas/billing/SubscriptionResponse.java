package com.leonardtrinh.supportsaas.billing;

import java.time.Instant;

public record SubscriptionResponse(
        String planSlug,
        String planName,
        String status,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        Instant trialEndsAt,
        String pendingDowngradePlan,
        boolean cancelAtPeriodEnd) {

    public static SubscriptionResponse from(Subscription subscription, Plan plan) {
        return new SubscriptionResponse(
                plan.getSlug(),
                plan.getName(),
                subscription.getStatus().name(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.getTrialEndsAt(),
                null,
                false);
    }
}
