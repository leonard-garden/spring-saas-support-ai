package com.leonardtrinh.supportsaas.invitation;

import com.leonardtrinh.supportsaas.member.Role;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record InvitationResponse(
    @Schema(description = "Invitation UUID") UUID id,
    @Schema(description = "Invited email address") String email,
    @Schema(description = "Assigned role") Role role,
    @Schema(description = "Invitation expiry timestamp") Instant expiresAt,
    @Schema(description = "Invitation creation timestamp") Instant createdAt
) {}
