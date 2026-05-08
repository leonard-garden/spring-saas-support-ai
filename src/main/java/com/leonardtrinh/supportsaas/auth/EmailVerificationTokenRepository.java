package com.leonardtrinh.supportsaas.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    @Query("SELECT t FROM EmailVerificationToken t JOIN FETCH t.member WHERE t.tokenHash = :hash AND t.usedAt IS NULL AND t.expiresAt > :now")
    Optional<EmailVerificationToken> findActiveByTokenHash(@Param("hash") String hash, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE EmailVerificationToken t SET t.usedAt = :now WHERE t.member.id = :memberId AND t.usedAt IS NULL")
    void invalidateAllActiveByMemberId(@Param("memberId") UUID memberId, @Param("now") Instant now);
}
