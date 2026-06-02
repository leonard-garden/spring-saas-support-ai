package com.leonardtrinh.supportsaas.billing;

import java.time.Instant;

public record DowngradeResponse(String message, String targetPlan, Instant effectiveAt) {

    public static DowngradeResponse of(String targetPlanName, Instant effectiveAt) {
        return new DowngradeResponse(
                "Downgrade scheduled. Your plan will change at the end of the current billing period.",
                targetPlanName,
                effectiveAt);
    }
}
