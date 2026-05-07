package com.leonardtrinh.supportsaas.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Query("SELECT t FROM RefreshToken t JOIN FETCH t.member WHERE t.tokenHash = :hash AND t.revokedAt IS NULL AND t.expiresAt > :now")
    Optional<RefreshToken> findActiveByTokenHash(@Param("hash") String hash, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.tokenHash = :hash AND t.revokedAt IS NULL")
    void revokeByTokenHash(@Param("hash") String hash, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.member.id = :memberId AND t.revokedAt IS NULL")
    void revokeAllByMemberId(@Param("memberId") UUID memberId, @Param("now") Instant now);
}
