package com.leonardtrinh.supportsaas.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
    @Schema(description = "Password reset token from the email link")
    @NotBlank String token,
    @Schema(description = "New password — minimum 8 characters", example = "newSecret123")
    @NotBlank @Size(min = 8) String newPassword
) {}
