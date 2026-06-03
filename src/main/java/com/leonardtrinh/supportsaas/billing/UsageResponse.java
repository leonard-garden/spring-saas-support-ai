package com.leonardtrinh.supportsaas.billing;

public record UsageResponse(
        UsageMetric knowledgeBases,
        UsageMetric documents,
        UsageMetric messages,
        UsageMetric members) {}
