package com.leonardtrinh.supportsaas.auth;

import java.util.UUID;

public record JwtClaims(UUID memberId, UUID tenantId, String role, String email, String jti) {}
