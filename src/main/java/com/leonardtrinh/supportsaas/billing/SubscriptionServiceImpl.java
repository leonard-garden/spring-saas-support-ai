package com.leonardtrinh.supportsaas.billing;

import com.leonardtrinh.supportsaas.auth.PlanMisconfiguredException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SubscriptionServiceImpl implements SubscriptionService {

    private static final String PLAN_PRO = "pro";
    private static final String PLAN_FREE = "free";
    private static final int TRIAL_DAYS = 14;

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;

    public SubscriptionServiceImpl(SubscriptionRepository subscriptionRepository,
                                   PlanRepository planRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
    }

    @Override
    @Transactional
    public Subscription createTrial(UUID businessId) {
        Plan pro = planRepository.findBySlug(PLAN_PRO)
                .orElseThrow(() -> new PlanMisconfiguredException(PLAN_PRO));

        Instant now = Instant.now();
        Instant trialEndsAt = now.plus(TRIAL_DAYS, ChronoUnit.DAYS);

        Subscription sub = new Subscription();
        sub.setBusinessId(businessId);
        sub.setPlanId(pro.getId());
        sub.setStatus(SubscriptionStatus.TRIALING);
        sub.setTrialEndsAt(trialEndsAt);
        sub.setCurrentPeriodStart(now);
        sub.setCurrentPeriodEnd(trialEndsAt);

        return subscriptionRepository.save(sub);
    }

    @Override
    public Optional<Subscription> getCurrentSubscription(UUID businessId) {
        return subscriptionRepository.findActiveByBusinessId(businessId);
    }

    @Override
    public Optional<Plan> getCurrentPlan(UUID businessId) {
        return getCurrentSubscription(businessId)
                .flatMap(sub -> planRepository.findById(sub.getPlanId()));
    }

    @Override
    public boolean isActivePaid(UUID businessId) {
        return getCurrentPlan(businessId)
                .map(plan -> !PLAN_FREE.equals(plan.getSlug()))
                .orElse(false);
    }

    @Override
    public List<Plan> getActivePlans() {
        return planRepository.findAll().stream()
                .filter(Plan::isActive)
                .toList();
    }
}
