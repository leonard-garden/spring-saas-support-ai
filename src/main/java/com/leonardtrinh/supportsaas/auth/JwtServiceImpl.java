package com.leonardtrinh.supportsaas.auth;

import com.leonardtrinh.supportsaas.member.Member;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtServiceImpl implements JwtService {

    private final JwtProperties properties;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SecretKey signingKey;

    public JwtServiceImpl(JwtProperties properties, RefreshTokenRepository refreshTokenRepository) {
        this.properties = properties;
        this.refreshTokenRepository = refreshTokenRepository;
        byte[] keyBytes = Base64.getDecoder().decode(properties.secret());
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                "JWT secret must be at least 256 bits (32 bytes). Got: " + keyBytes.length + " bytes");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public String generateAccessToken(Member member) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(member.getId().toString())
            .claim("tenant_id", member.getBusinessId().toString())
            .claim("role", member.getRole().name())
            .claim("email", member.getEmail())
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMillis(properties.accessTokenExpirationMs())))
            .signWith(signingKey)
            .compact();
    }

    @Override
    @Transactional
    public String generateRefreshToken(Member member) {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        String plaintext = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        RefreshToken token = new RefreshToken();
        token.setMember(member);
        token.setTokenHash(sha256Hex(plaintext));
        token.setExpiresAt(Instant.now().plusMillis(properties.refreshTokenExpirationMs()));
        refreshTokenRepository.save(token);

        return plaintext;
    }

    @Override
    public JwtClaims validateAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
            return new JwtClaims(
                UUID.fromString(claims.getSubject()),
                UUID.fromString(claims.get("tenant_id", String.class)),
                claims.get("role", String.class),
                claims.get("email", String.class),
                claims.getId()
            );
        } catch (ExpiredJwtException e) {
            throw new ExpiredTokenException("Access token has expired");
        } catch (JwtException e) {
            throw new InvalidTokenException("Invalid access token: " + e.getMessage());
        }
    }

    @Override
    public Member validateRefreshToken(String token) {
        String hash = sha256Hex(token);
        return refreshTokenRepository.findActiveByTokenHash(hash, Instant.now())
            .map(RefreshToken::getMember)
            .orElseThrow(() -> new InvalidTokenException("Refresh token is invalid or expired"));
    }

    @Override
    @Transactional
    public String rotateRefreshToken(String oldToken, Member member) {
        refreshTokenRepository.revokeByTokenHash(sha256Hex(oldToken), Instant.now());
        return generateRefreshToken(member);
    }

    @Override
    @Transactional
    public void revokeAllRefreshTokens(UUID memberId) {
        refreshTokenRepository.revokeAllByMemberId(memberId, Instant.now());
    }

    @Override
    @Transactional
    public void revokeRefreshToken(String rawToken) {
        refreshTokenRepository.revokeByTokenHash(sha256Hex(rawToken), Instant.now());
    }

    static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
