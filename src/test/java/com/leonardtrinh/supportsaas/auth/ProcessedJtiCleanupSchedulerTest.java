package com.leonardtrinh.supportsaas.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessedJtiCleanupSchedulerTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private ProcessedJtiCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ProcessedJtiCleanupScheduler(refreshTokenRepository);
    }

    @Test
    @DisplayName("purgeExpiredTokens calls deleteExpiredBefore with a past-or-present threshold")
    void purgeExpiredTokens_callsDeleteWithThreshold() {
        when(refreshTokenRepository.deleteExpiredBefore(any(Instant.class))).thenReturn(3);

        Instant before = Instant.now();
        scheduler.purgeExpiredTokens();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(refreshTokenRepository).deleteExpiredBefore(captor.capture());

        Instant threshold = captor.getValue();
        assertThat(threshold).isAfterOrEqualTo(before);
        assertThat(threshold).isBeforeOrEqualTo(after);
    }

    @Test
    @DisplayName("purgeExpiredTokens succeeds when no rows are deleted")
    void purgeExpiredTokens_zeroDeleted_noException() {
        when(refreshTokenRepository.deleteExpiredBefore(any(Instant.class))).thenReturn(0);

        scheduler.purgeExpiredTokens(); // must not throw

        verify(refreshTokenRepository).deleteExpiredBefore(any(Instant.class));
    }

    @Test
    @DisplayName("purgeExpiredTokens threshold is not in the future")
    void purgeExpiredTokens_thresholdIsNotFuture() {
        when(refreshTokenRepository.deleteExpiredBefore(any(Instant.class))).thenReturn(0);

        Instant before = Instant.now();
        scheduler.purgeExpiredTokens();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(refreshTokenRepository).deleteExpiredBefore(captor.capture());

        assertThat(captor.getValue()).isBeforeOrEqualTo(Instant.now());
        assertThat(captor.getValue()).isAfterOrEqualTo(before.minusMillis(100));
    }
}
