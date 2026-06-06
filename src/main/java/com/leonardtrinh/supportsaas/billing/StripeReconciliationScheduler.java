package com.leonardtrinh.supportsaas.billing;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Daily scheduler that reconciles subscription statuses in the DB against Stripe.
 *
 * <p>Runs at 03:30 UTC every day. Detects and corrects mismatches caused by missed or
 * delayed Stripe webhooks, ensuring tenants are never stuck in a wrong billing state.
 *
 * <p>Does NOT use TenantContext — processes subscriptions across all tenants using
 * SubscriptionRepository queries that bypass the Hibernate tenant filter.
 */
@Slf4j
@Component
public class StripeReconciliationScheduler {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;
    private final StripeService stripeService;
    private final Counter mismatchCounter;

    public StripeReconciliationScheduler(SubscriptionRepository subscriptionRepository,
                                         SubscriptionService subscriptionService,
                                         StripeService stripeService,
                                         MeterRegistry meterRegistry) {
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionService = subscriptionService;
        this.stripeService = stripeService;
        this.mismatchCounter = Counter.builder("stripe.reconciliation.mismatch")
                .description("Total number of subscription status mismatches corrected from Stripe")
                .register(meterRegistry);
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void reconcile() {
        List<Subscription> candidates = subscriptionRepository.findAllByStatusIn(
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE, SubscriptionStatus.TRIALING));

        if (candidates.isEmpty()) {
            log.debug("StripeReconciliationScheduler: no active subscriptions to reconcile");
            return;
        }

        log.info("StripeReconciliationScheduler: reconciling {} subscription(s)", candidates.size());

        for (Subscription subscription : candidates) {
            if (subscription.getStripeSubscriptionId() == null) {
                log.debug("StripeReconciliationScheduler: skipping subscriptionId={} — no stripeSubscriptionId",
                        subscription.getId());
                continue;
            }

            try {
                com.stripe.model.Subscription stripeSubscription =
                        stripeService.retrieveSubscription(subscription.getStripeSubscriptionId());

                SubscriptionStatus stripeStatus = mapStripeStatus(stripeSubscription.getStatus());

                if (subscription.getStatus() != stripeStatus) {
                    log.warn("StripeReconciliationScheduler: mismatch detected subscriptionId={} "
                                    + "businessId={} dbStatus={} stripeStatus={}",
                            subscription.getId(), subscription.getBusinessId(),
                            subscription.getStatus(), stripeStatus);

                    subscriptionService.syncFromStripe(subscription);
                    mismatchCounter.increment();
                }
            } catch (Exception ex) {
                log.error("StripeReconciliationScheduler: error reconciling subscriptionId={} — {}",
                        subscription.getId(), ex.getMessage(), ex);
            }
        }
    }

    private SubscriptionStatus mapStripeStatus(String stripeStatus) {
        return switch (stripeStatus) {
            case "active" -> SubscriptionStatus.ACTIVE;
            case "trialing" -> SubscriptionStatus.TRIALING;
            case "past_due" -> SubscriptionStatus.PAST_DUE;
            case "canceled", "cancelled" -> SubscriptionStatus.CANCELED;
            case "unpaid" -> SubscriptionStatus.UNPAID;
            default -> SubscriptionStatus.PAST_DUE;
        };
    }
}
