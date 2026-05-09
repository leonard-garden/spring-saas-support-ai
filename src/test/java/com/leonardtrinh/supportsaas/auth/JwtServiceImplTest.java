package com.leonardtrinh.supportsaas.auth;

import com.leonardtrinh.supportsaas.member.Member;
import com.leonardtrinh.supportsaas.member.MemberRepository;
import com.leonardtrinh.supportsaas.member.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtServiceImplTest {

    // 44-byte base64 key → decodes to 33 bytes (> 32 required)
    private static final String TEST_SECRET =
        Base64.getEncoder().encodeToString("this-is-a-test-secret-key-32bytes!".getBytes());

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private MemberRepository memberRepository;

    private JwtServiceImpl jwtService;
    private Member member;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(TEST_SECRET, 900_000L, 604_800_000L);
        jwtService = new JwtServiceImpl(properties, refreshTokenRepository, memberRepository);

        member = new Member();
        member.setId(UUID.randomUUID());
        member.setBusinessId(UUID.randomUUID());
        member.setEmail("user@example.com");
        member.setRole(Role.ADMIN);
    }

    @Test
    @DisplayName("generateAccessToken → validateAccessToken roundtrip returns correct claims")
    void generateAndValidate_roundtrip_returnsCorrectClaims() {
        String token = jwtService.generateAccessToken(member);
        JwtClaims claims = jwtService.validateAccessToken(token);

        assertThat(claims.memberId()).isEqualTo(member.getId());
        assertThat(claims.tenantId()).isEqualTo(member.getBusinessId());
        assertThat(claims.role()).isEqualTo("ADMIN");
        assertThat(claims.email()).isEqualTo("user@example.com");
        assertThat(claims.jti()).isNotBlank();
    }

    @Test
    @DisplayName("validateAccessToken throws ExpiredTokenException for expired token")
    void validateAccessToken_expiredToken_throwsExpiredTokenException() {
        JwtProperties shortExpiry = new JwtProperties(TEST_SECRET, -1L, 604_800_000L);
        JwtServiceImpl shortService = new JwtServiceImpl(shortExpiry, refreshTokenRepository, memberRepository);

        String token = shortService.generateAccessToken(member);

        assertThatThrownBy(() -> jwtService.validateAccessToken(token))
            .isInstanceOf(ExpiredTokenException.class);
    }

    @Test
    @DisplayName("validateAccessToken throws InvalidTokenException for tampered token")
    void validateAccessToken_tamperedToken_throwsInvalidTokenException() {
        String token = jwtService.generateAccessToken(member);
        String tampered = token.substring(0, token.length() - 4) + "xxxx";

        assertThatThrownBy(() -> jwtService.validateAccessToken(tampered))
            .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("generateRefreshToken stores SHA-256 hash, not plaintext")
    void generateRefreshToken_storesHashNotPlaintext() {
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String plaintext = jwtService.generateRefreshToken(member);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());

        String storedHash = captor.getValue().getTokenHash();
        assertThat(storedHash).isNotEqualTo(plaintext);
        assertThat(storedHash).isEqualTo(JwtServiceImpl.sha256Hex(plaintext));
        assertThat(storedHash).hasSize(64); // SHA-256 hex = 64 chars
    }

    @Test
    @DisplayName("rotateRefreshToken revokes old token and issues new one")
    void rotateRefreshToken_revokesOldAndIssuesNew() {
        String oldToken = "old-plaintext-token";
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String newToken = jwtService.rotateRefreshToken(oldToken, member);

        verify(refreshTokenRepository).revokeByTokenHash(
            eq(JwtServiceImpl.sha256Hex(oldToken)), any(Instant.class));
        assertThat(newToken).isNotEqualTo(oldToken);
        assertThat(newToken).isNotBlank();
    }

    @Test
    @DisplayName("validateRefreshToken throws InvalidTokenException when token not found")
    void validateRefreshToken_notFound_throwsInvalidTokenException() {
        when(refreshTokenRepository.findActiveByTokenHash(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jwtService.validateRefreshToken("no-such-token"))
            .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("revokeAllRefreshTokens delegates to repository with member id")
    void revokeAllRefreshTokens_callsRepository() {
        UUID memberId = UUID.randomUUID();
        jwtService.revokeAllRefreshTokens(memberId);
        verify(refreshTokenRepository).revokeAllByMemberId(eq(memberId), any(Instant.class));
    }

    @Test
    @DisplayName("sha256Hex produces consistent output for same input")
    void sha256Hex_consistentOutput() {
        String input = "test-input";
        assertThat(JwtServiceImpl.sha256Hex(input))
            .isEqualTo(JwtServiceImpl.sha256Hex(input))
            .hasSize(64);
    }

    @Test
    @DisplayName("JwtServiceImpl fails fast when secret is too short")
    void constructor_shortSecret_throwsIllegalState() {
        String shortSecret = Base64.getEncoder().encodeToString("short".getBytes());
        JwtProperties bad = new JwtProperties(shortSecret, 900_000L, 604_800_000L);

        assertThatThrownBy(() -> new JwtServiceImpl(bad, refreshTokenRepository, memberRepository))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("256 bits");
    }
}
