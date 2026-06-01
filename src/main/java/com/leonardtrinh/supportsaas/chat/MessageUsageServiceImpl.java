package com.leonardtrinh.supportsaas.chat;

import com.leonardtrinh.supportsaas.billing.Plan;
import com.leonardtrinh.supportsaas.billing.PlanRepository;
import com.leonardtrinh.supportsaas.billing.QuotaExceededException;
import com.leonardtrinh.supportsaas.billing.Subscription;
import com.leonardtrinh.supportsaas.billing.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class MessageUsageServiceImpl implements MessageUsageService {

    // Fallback limit used when no active subscription is found (Free plan equivalent)
    private static final int FREE_PLAN_MSG_LIMIT = 100;

    private final MessageUsageRepository usageRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;

    public MessageUsageServiceImpl(
            MessageUsageRepository usageRepository,
            SubscriptionRepository subscriptionRepository,
            PlanRepository planRepository) {
        this.usageRepository = usageRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
    }

    @Override
    public void checkQuota(UUID businessId, String yearMonth) {
        int limit = resolveLimit(businessId);
        long current = usageRepository.findByBusinessIdAndYearMonth(businessId, yearMonth)
                .map(u -> (long) u.getMsgCount())
                .orElse(0L);

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

    private int resolveLimit(UUID businessId) {
        return subscriptionRepository.findActiveByBusinessId(businessId)
                .map(Subscription::getPlanId)
                .flatMap(planRepository::findById)
                .map(Plan::getMaxMessagesPerMonth)
                .orElse(FREE_PLAN_MSG_LIMIT);
    }
}
