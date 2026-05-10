package com.leonardtrinh.supportsaas.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
    @Schema(description = "Name of your company or workspace", example = "Acme Corp")
    @NotBlank(message = "Business name is required")
    String businessName,

    @Schema(description = "Owner's email address", example = "owner@acme.com")
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    String email,

    @Schema(description = "Password — minimum 8 characters", example = "secret123")
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    String password
) {}
