package com.leonardtrinh.supportsaas.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @Schema(description = "Registered email address", example = "owner@acme.com")
    @NotBlank(message = "Email is required")
    String email,

    @Schema(description = "Account password", example = "secret123")
    @NotBlank(message = "Password is required")
    String password
) {}
