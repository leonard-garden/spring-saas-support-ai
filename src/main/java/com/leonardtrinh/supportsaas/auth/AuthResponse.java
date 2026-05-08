package com.leonardtrinh.supportsaas.auth;

import java.util.UUID;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    UUID businessId,
    UUID memberId
) {}
