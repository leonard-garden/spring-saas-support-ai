package com.leonardtrinh.supportsaas.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
    @Schema(description = "Refresh token obtained from login or previous refresh")
    @NotBlank(message = "Refresh token is required")
    String refreshToken
) {}
