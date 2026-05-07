package com.leonardtrinh.supportsaas.auth;

import com.leonardtrinh.supportsaas.member.Member;

import java.util.UUID;

public interface JwtService {

    String generateAccessToken(Member member);

    String generateRefreshToken(Member member);

    JwtClaims validateAccessToken(String token);

    Member validateRefreshToken(String token);

    String rotateRefreshToken(String oldToken, Member member);

    void revokeAllRefreshTokens(UUID memberId);
}
