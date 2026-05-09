package com.leonardtrinh.supportsaas.member;

import java.time.Instant;
import java.util.UUID;

public record MemberResponse(
    UUID id,
    String email,
    String role,
    Instant createdAt
) {}
