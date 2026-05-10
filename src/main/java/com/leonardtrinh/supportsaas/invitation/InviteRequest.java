package com.leonardtrinh.supportsaas.invitation;

import com.leonardtrinh.supportsaas.member.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InviteRequest(
    @Schema(description = "Email to invite", example = "agent@acme.com")
    @NotBlank @Email String email,
    @Schema(description = "Role to assign: ADMIN or AGENT")
    @NotNull Role role
) {}
