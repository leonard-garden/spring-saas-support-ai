package com.leonardtrinh.supportsaas.member;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record MemberResponse(
    @Schema(description = "Member UUID") UUID id,
    @Schema(description = "Email address") String email,
    @Schema(description = "Role: OWNER, ADMIN, or AGENT") String role,
    @Schema(description = "Member creation timestamp") Instant createdAt
) {}
