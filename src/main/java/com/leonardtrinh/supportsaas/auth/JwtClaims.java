package com.leonardtrinh.supportsaas.auth;

import java.util.UUID;

public record JwtClaims(UUID userId, UUID tenantId, String role, String email) {}
