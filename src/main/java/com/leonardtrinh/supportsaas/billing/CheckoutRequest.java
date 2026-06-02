package com.leonardtrinh.supportsaas.billing;

import jakarta.validation.constraints.NotBlank;

public record CheckoutRequest(@NotBlank String planSlug) {}
