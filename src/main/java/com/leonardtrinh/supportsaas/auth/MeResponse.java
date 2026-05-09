package com.leonardtrinh.supportsaas.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record MeResponse(
    @Schema(description = "Member UUID") UUID id,
    @Schema(description = "Email address") String email,
    @Schema(description = "Role: OWNER, ADMIN, or AGENT") String role,
    @Schema(description = "Tenant business UUID") UUID businessId,
    @Schema(description = "Business display name") String businessName,
    @Schema(description = "Whether email has been verified") boolean emailVerified
) {}
