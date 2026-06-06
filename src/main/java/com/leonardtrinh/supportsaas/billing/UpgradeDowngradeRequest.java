package com.leonardtrinh.supportsaas.billing;

import jakarta.validation.constraints.NotBlank;

public record UpgradeDowngradeRequest(@NotBlank String planSlug) {}
