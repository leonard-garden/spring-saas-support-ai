package com.leonardtrinh.supportsaas.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Weekly scheduler that purges expired refresh tokens from the refresh_tokens table.
 * Runs every Monday at 03:00 UTC to prevent unbounded table growth (FR-033).
 */
@Slf4j
@Component
public class ProcessedJtiCleanupScheduler {

    private final RefreshTokenRepository refreshTokenRepository;

    public ProcessedJtiCleanupScheduler(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Scheduled(cron = "0 0 3 * * MON")
    @Transactional
    public void purgeExpiredTokens() {
        Instant now = Instant.now();
        int deleted = refreshTokenRepository.deleteExpiredBefore(now);
        log.info("JTI cleanup: purged {} expired refresh_tokens (threshold={})", deleted, now);
    }
}
