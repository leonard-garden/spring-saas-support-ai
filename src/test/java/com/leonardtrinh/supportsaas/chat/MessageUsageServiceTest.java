package com.leonardtrinh.supportsaas.chat;

import com.leonardtrinh.supportsaas.billing.Plan;
import com.leonardtrinh.supportsaas.billing.PlanRepository;
import com.leonardtrinh.supportsaas.billing.QuotaExceededException;
import com.leonardtrinh.supportsaas.billing.Subscription;
import com.leonardtrinh.supportsaas.billing.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageUsageServiceTest {

    @Mock
    private MessageUsageRepository usageRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PlanRepository planRepository;

    private MessageUsageServiceImpl messageUsageService;

    private static final UUID BUSINESS_ID = UUID.randomUUID();
    private static final String YEAR_MONTH = "2026-05";
    private static final Instant PERIOD_START = Instant.parse("2026-05-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        messageUsageService = new MessageUsageServiceImpl(
            usageRepository, chatMessageRepository, subscriptionRepository, planRepository);
    }

    @Test
    @DisplayName("checkQuota — within limit does not throw")
    void checkQuota_withinLimit() {
        // given — plan allows 1000, subscription period started 2026-05-01, current count is 42
        UUID planId = UUID.randomUUID();
        Subscription sub = mock(Subscription.class);
        when(sub.getPlanId()).thenReturn(planId);
        when(sub.getCurrentPeriodStart()).thenReturn(PERIOD_START);
        Plan plan = mock(Plan.class);
        when(plan.getMaxMessagesPerMonth()).thenReturn(1000);

        when(subscriptionRepository.findActiveByBusinessId(BUSINESS_ID))
            .thenReturn(Optional.of(sub));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(chatMessageRepository.countByBusinessIdAndRoleAndCreatedAtGreaterThanEqual(
                eq(BUSINESS_ID), eq(MessageRole.USER), eq(PERIOD_START)))
            .thenReturn(42L);

        // when/then — 42 < 1000, no exception
        assertThatCode(() -> messageUsageService.checkQuota(BUSINESS_ID, YEAR_MONTH))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("checkQuota — at limit throws QuotaExceededException")
    void checkQuota_exceeded() {
        // given — plan allows 100, count is 100 (at limit)
        UUID planId = UUID.randomUUID();
        Subscription sub = mock(Subscription.class);
        when(sub.getPlanId()).thenReturn(planId);
        when(sub.getCurrentPeriodStart()).thenReturn(PERIOD_START);
        Plan plan = mock(Plan.class);
        when(plan.getMaxMessagesPerMonth()).thenReturn(100);

        when(subscriptionRepository.findActiveByBusinessId(BUSINESS_ID))
            .thenReturn(Optional.of(sub));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(chatMessageRepository.countByBusinessIdAndRoleAndCreatedAtGreaterThanEqual(
                eq(BUSINESS_ID), eq(MessageRole.USER), eq(PERIOD_START)))
            .thenReturn(100L);

        // when/then
        assertThatThrownBy(() -> messageUsageService.checkQuota(BUSINESS_ID, YEAR_MONTH))
            .isInstanceOf(QuotaExceededException.class)
            .satisfies(ex -> {
                QuotaExceededException qe = (QuotaExceededException) ex;
                org.assertj.core.api.Assertions.assertThat(qe.getMetric()).isEqualTo("messages_per_month");
                org.assertj.core.api.Assertions.assertThat(qe.getLimit()).isEqualTo(100L);
                org.assertj.core.api.Assertions.assertThat(qe.getCurrent()).isEqualTo(100L);
            });
    }

    @Test
    @DisplayName("checkQuota — no active subscription falls back to free limit and start-of-month window")
    void checkQuota_noSubscription_usesFallback() {
        // given — no subscription; free limit is 100, current count is 50
        when(subscriptionRepository.findActiveByBusinessId(BUSINESS_ID))
            .thenReturn(Optional.empty());
        // Any Instant is passed as the start-of-month fallback; count is below free limit
        when(chatMessageRepository.countByBusinessIdAndRoleAndCreatedAtGreaterThanEqual(
                eq(BUSINESS_ID), eq(MessageRole.USER), any(Instant.class)))
            .thenReturn(50L);

        // when/then — 50 < 100 free limit, no exception
        assertThatCode(() -> messageUsageService.checkQuota(BUSINESS_ID, YEAR_MONTH))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("checkQuota — no subscription at free limit throws QuotaExceededException")
    void checkQuota_noSubscription_atFreeLimit_throws() {
        // given — no subscription; free limit is 100, count is 100
        when(subscriptionRepository.findActiveByBusinessId(BUSINESS_ID))
            .thenReturn(Optional.empty());
        when(chatMessageRepository.countByBusinessIdAndRoleAndCreatedAtGreaterThanEqual(
                eq(BUSINESS_ID), eq(MessageRole.USER), any(Instant.class)))
            .thenReturn(100L);

        // when/then
        assertThatThrownBy(() -> messageUsageService.checkQuota(BUSINESS_ID, YEAR_MONTH))
            .isInstanceOf(QuotaExceededException.class)
            .satisfies(ex -> {
                QuotaExceededException qe = (QuotaExceededException) ex;
                org.assertj.core.api.Assertions.assertThat(qe.getLimit()).isEqualTo(100L);
            });
    }

    @Test
    @DisplayName("checkQuota — subscription with null currentPeriodStart falls back to start-of-month")
    void checkQuota_nullPeriodStart_fallsBackToStartOfMonth() {
        // given — subscription exists but currentPeriodStart is null (e.g. legacy row)
        UUID planId = UUID.randomUUID();
        Subscription sub = mock(Subscription.class);
        when(sub.getPlanId()).thenReturn(planId);
        when(sub.getCurrentPeriodStart()).thenReturn(null);
        Plan plan = mock(Plan.class);
        when(plan.getMaxMessagesPerMonth()).thenReturn(1000);

        when(subscriptionRepository.findActiveByBusinessId(BUSINESS_ID))
            .thenReturn(Optional.of(sub));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(chatMessageRepository.countByBusinessIdAndRoleAndCreatedAtGreaterThanEqual(
                eq(BUSINESS_ID), eq(MessageRole.USER), any(Instant.class)))
            .thenReturn(5L);

        // when/then — start-of-month used as fallback, 5 < 1000, no exception
        assertThatCode(() -> messageUsageService.checkQuota(BUSINESS_ID, YEAR_MONTH))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("increment — calls upsert query on repository")
    void increment_callsUpsertQuery() {
        // given
        doNothing().when(usageRepository).upsertIncrement(any(), any());

        // when
        messageUsageService.increment(BUSINESS_ID, YEAR_MONTH);

        // then
        verify(usageRepository).upsertIncrement(BUSINESS_ID, YEAR_MONTH);
    }
}
