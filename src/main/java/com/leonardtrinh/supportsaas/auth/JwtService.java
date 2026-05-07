package com.leonardtrinh.supportsaas.auth;

public interface JwtService {

    JwtClaims validateToken(String token);

    String generateAccessToken(JwtClaims claims);

    String generateRefreshToken(JwtClaims claims);
}
