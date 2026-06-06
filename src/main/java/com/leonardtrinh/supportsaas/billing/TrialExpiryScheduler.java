package com.leonardtrinh.supportsaas.billing;

import com.leonardtrinh.supportsaas.email.AsyncEmailSender;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Daily scheduler that downgrades expired trials to the Free plan and notifies owners.
 *
 * <p>Runs at 02:00 UTC every day. Idempotent: re-running produces the same result because
 * it queries only TRIALING subscriptions with trialEndsAt in the past, and immediately
 * sets status=ACTIVE (Free). A second run finds no eligible rows.
 *
 * <p>Does NOT use TenantContext — queries native SQL via SubscriptionRepository which
 * already bypasses the Hibernate tenant filter.
 */
@Slf4j
@Component
public class TrialExpiryScheduler {

    private static final String PLAN_FREE = "free";

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final AsyncEmailSender asyncEmailSender;
    private final Counter trialExpiredCounter;

    public TrialExpiryScheduler(SubscriptionRepository subscriptionRepository,
                                PlanRepository planRepository,
                                AsyncEmailSender asyncEmailSender,
                                MeterRegistry meterRegistry) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.asyncEmailSender = asyncEmailSender;
        this.trialExpiredCounter = Counter.builder("trial.expired.total")
                .description("Total number of trials downgraded to Free plan")
                .register(meterRegistry);
    }

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void downgradeExpiredTrials() {
        Instant now = Instant.now();
        List<Subscription> expired =
                subscriptionRepository.findAllByStatusAndTrialEndsAtBefore(SubscriptionStatus.TRIALING, now);

        if (expired.isEmpty()) {
            log.debug("TrialExpiryScheduler: no expired trials found");
            return;
        }

        Plan freePlan = planRepository.findBySlug(PLAN_FREE).orElse(null);
        if (freePlan == null) {
            log.error("TrialExpiryScheduler: Free plan not found in database — aborting downgrade");
            return;
        }

        log.info("TrialExpiryScheduler: downgrading {} expired trial(s) to Free plan", expired.size());

        for (Subscription subscription : expired) {
            subscription.setPlanId(freePlan.getId());
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setUpdatedAt(now);
            subscriptionRepository.save(subscription);

            subscriptionRepository.findOwnerEmailByBusinessId(subscription.getBusinessId())
                    .ifPresentOrElse(
                            email -> asyncEmailSender.sendTrialExpiredAsync(email),
                            () -> log.warn("TrialExpiryScheduler: no owner email found for businessId={}",
                                    subscription.getBusinessId())
                    );

            trialExpiredCounter.increment();
            log.info("TrialExpiryScheduler: downgraded subscriptionId={} businessId={}",
                    subscription.getId(), subscription.getBusinessId());
        }
    }
}
