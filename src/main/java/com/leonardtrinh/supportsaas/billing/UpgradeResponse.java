package com.leonardtrinh.supportsaas.billing;

public record UpgradeResponse(String message, String targetPlan) {

    public static UpgradeResponse of(String targetPlanName) {
        return new UpgradeResponse(
                "Plan upgraded immediately. Subscription record will reflect the change after webhook confirmation.",
                targetPlanName);
    }
}
