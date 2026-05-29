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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageUsageServiceTest {

    @Mock
    private MessageUsageRepository usageRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PlanRepository planRepository;

    private MessageUsageServiceImpl messageUsageService;

    private static final UUID BUSINESS_ID = UUID.randomUUID();
    private static final String YEAR_MONTH = "2026-05";

    @BeforeEach
    void setUp() {
        messageUsageService = new MessageUsageServiceImpl(
            usageRepository, subscriptionRepository, planRepository);
    }

    @Test
    @DisplayName("checkQuota — within limit does not throw")
    void checkQuota_withinLimit() {
        // given — plan allows 1000, current usage is 42
        UUID planId = UUID.randomUUID();
        Subscription sub = mock(Subscription.class);
        when(sub.getPlanId()).thenReturn(planId);
        Plan plan = mock(Plan.class);
        when(plan.getMaxMessagesPerMonth()).thenReturn(1000);

        when(subscriptionRepository.findActiveByBusinessId(BUSINESS_ID))
            .thenReturn(Optional.of(sub));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

        MessageUsage usage = new MessageUsage();
        usage.setMsgCount(42);
        when(usageRepository.findByBusinessIdAndYearMonth(BUSINESS_ID, YEAR_MONTH))
            .thenReturn(Optional.of(usage));

        // when/then — no exception
        assertThatCode(() -> messageUsageService.checkQuota(BUSINESS_ID, YEAR_MONTH))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("checkQuota — at limit throws QuotaExceededException")
    void checkQuota_exceeded() {
        // given — plan allows 100, current usage is 100 (at limit)
        UUID planId = UUID.randomUUID();
        Subscription sub = mock(Subscription.class);
        when(sub.getPlanId()).thenReturn(planId);
        Plan plan = mock(Plan.class);
        when(plan.getMaxMessagesPerMonth()).thenReturn(100);

        when(subscriptionRepository.findActiveByBusinessId(BUSINESS_ID))
            .thenReturn(Optional.of(sub));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

        MessageUsage usage = new MessageUsage();
        usage.setMsgCount(100);
        when(usageRepository.findByBusinessIdAndYearMonth(BUSINESS_ID, YEAR_MONTH))
            .thenReturn(Optional.of(usage));

        // when/then
        assertThatThrownBy(() -> messageUsageService.checkQuota(BUSINESS_ID, YEAR_MONTH))
            .isInstanceOf(QuotaExceededException.class);
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

    @Test
    @DisplayName("checkQuota — no active subscription uses free plan fallback limit")
    void checkQuota_noSubscription_usesFallback() {
        // given — no subscription, free plan limit is 100, current is 50
        when(subscriptionRepository.findActiveByBusinessId(BUSINESS_ID))
            .thenReturn(Optional.empty());

        MessageUsage usage = new MessageUsage();
        usage.setMsgCount(50);
        when(usageRepository.findByBusinessIdAndYearMonth(BUSINESS_ID, YEAR_MONTH))
            .thenReturn(Optional.of(usage));

        // when/then — 50 < 100 free limit, no exception
        assertThatCode(() -> messageUsageService.checkQuota(BUSINESS_ID, YEAR_MONTH))
            .doesNotThrowAnyException();
    }
}
