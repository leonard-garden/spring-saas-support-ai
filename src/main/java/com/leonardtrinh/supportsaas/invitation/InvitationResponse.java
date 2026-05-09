package com.leonardtrinh.supportsaas.invitation;

import com.leonardtrinh.supportsaas.member.Role;

import java.time.Instant;
import java.util.UUID;

public record InvitationResponse(
    UUID id,
    String email,
    Role role,
    Instant expiresAt,
    Instant createdAt
) {}
