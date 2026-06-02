package com.leonardtrinh.supportsaas.billing;

import java.math.BigDecimal;

public record PlanResponse(
        String slug,
        String name,
        BigDecimal priceMonthly,
        Integer maxKnowledgeBases,
        Integer maxDocsPerKb,
        Integer maxMessagesPerMonth,
        Integer maxMembers) {

    public static PlanResponse from(Plan plan) {
        return new PlanResponse(
                plan.getSlug(),
                plan.getName(),
                plan.getPriceUsdMonthly(),
                plan.getMaxKnowledgeBases(),
                plan.getMaxDocumentsPerKb(),
                plan.getMaxMessagesPerMonth(),
                plan.getMaxMembers());
    }
}
