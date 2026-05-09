package com.leonardtrinh.supportsaas.invitation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptInvitationRequest(
    @Schema(description = "Invitation token from the email link")
    @NotBlank String token,
    @Schema(description = "Password to set for the new account — minimum 8 characters", example = "secret123")
    @NotBlank @Size(min = 8) String password
) {}
