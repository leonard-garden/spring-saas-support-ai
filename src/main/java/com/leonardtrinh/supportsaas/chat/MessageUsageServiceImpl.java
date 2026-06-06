package com.leonardtrinh.supportsaas.chat;

import com.leonardtrinh.supportsaas.billing.Plan;
import com.leonardtrinh.supportsaas.billing.PlanRepository;
import com.leonardtrinh.supportsaas.billing.QuotaExceededException;
import com.leonardtrinh.supportsaas.billing.Subscription;
import com.leonardtrinh.supportsaas.billing.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class MessageUsageServiceImpl implements MessageUsageService {

    // Fallback limit used when no active subscription is found (Free plan equivalent)
    private static final int FREE_PLAN_MSG_LIMIT = 100;

    private final MessageUsageRepository usageRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;

    public MessageUsageServiceImpl(
            MessageUsageRepository usageRepository,
            ChatMessageRepository chatMessageRepository,
            SubscriptionRepository subscriptionRepository,
            PlanRepository planRepository) {
        this.usageRepository = usageRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
    }

    /**
     * Checks message quota for the current billing period.
     *
     * <p>The billing window is determined by {@code subscription.currentPeriodStart} so that
     * the quota resets naturally when Stripe updates the period — no separate cron job needed.
     * Falls back to the start of the current calendar month when no active subscription exists.
     *
     * @param businessId the tenant's business UUID
     * @param yearMonth  kept in signature for backward compatibility with callers; unused here
     */
    @Override
    public void checkQuota(UUID businessId, String yearMonth) {
        Optional<Subscription> subOpt = subscriptionRepository.findActiveByBusinessId(businessId);

        int limit = subOpt
                .map(Subscription::getPlanId)
                .flatMap(planRepository::findById)
                .map(Plan::getMaxMessagesPerMonth)
                .orElse(FREE_PLAN_MSG_LIMIT);

        Instant periodStart = subOpt
                .map(Subscription::getCurrentPeriodStart)
                .orElseGet(MessageUsageServiceImpl::startOfCurrentMonth);

        long current = chatMessageRepository.countByBusinessIdAndRoleAndCreatedAtGreaterThanEqual(
                businessId, MessageRole.USER, periodStart);

        if (current >= limit) {
            throw new QuotaExceededException("messages_per_month", limit, current);
        }
    }

    @Override
    @Transactional
    public void increment(UUID businessId, String yearMonth) {
        usageRepository.upsertIncrement(businessId, yearMonth);
    }

    // --- private helpers ---

    /**
     * Returns the UTC start of the current calendar month.
     * Used as a fallback period window when no active subscription exists.
     */
    private static Instant startOfCurrentMonth() {
        return ZonedDateTime.now(ZoneOffset.UTC)
                .withDayOfMonth(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0)
                .toInstant();
    }
}
