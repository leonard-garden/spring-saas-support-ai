package com.leonardtrinh.supportsaas.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
    @Schema(description = "Registered email address to send reset link to", example = "owner@acme.com")
    @NotBlank @Email String email
) {}
