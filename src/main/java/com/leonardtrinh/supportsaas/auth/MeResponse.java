package com.leonardtrinh.supportsaas.auth;

import java.util.UUID;

public record MeResponse(
    UUID id,
    String email,
    String role,
    UUID businessId,
    String businessName,
    boolean emailVerified
) {}
