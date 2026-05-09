package com.leonardtrinh.supportsaas.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record AuthResponse(
    @Schema(description = "Short-lived JWT access token (15 min)")
    String accessToken,
    @Schema(description = "Long-lived refresh token (7 days)")
    String refreshToken,
    @Schema(description = "Tenant (business) UUID")
    UUID businessId,
    @Schema(description = "Authenticated member UUID")
    UUID memberId
) {}
