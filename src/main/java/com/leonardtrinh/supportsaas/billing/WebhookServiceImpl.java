package com.leonardtrinh.supportsaas.billing;

import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class WebhookServiceImpl implements WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookServiceImpl.class);

    private final ProcessedWebhookEventRepository processedWebhookEventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;
    private final PlanRepository planRepository;

    public WebhookServiceImpl(
            ProcessedWebhookEventRepository processedWebhookEventRepository,
            SubscriptionRepository subscriptionRepository,
            SubscriptionService subscriptionService,
            PlanRepository planRepository) {
        this.processedWebhookEventRepository = processedWebhookEventRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionService = subscriptionService;
        this.planRepository = planRepository;
    }

    @Override
    @Transactional
    public void handle(Event event) {
        String eventId = event.getId();
        String eventType = event.getType();

        // Idempotency gate: skip already-processed events
        if (processedWebhookEventRepository.existsByStripeEventId(eventId)) {
            log.info("webhook_event_skipped event_id={} type={} reason=already_processed", eventId, eventType);
            return;
        }

        // Record the event before delegating to prevent duplicate execution on retry
        processedWebhookEventRepository.save(new ProcessedWebhookEvent(eventId, eventType));

        log.info("webhook_event_received event_id={} type={}", eventId, eventType);

        switch (eventType) {
            case "customer.subscription.created",
                 "customer.subscription.deleted" -> handleSubscriptionEvent(event);
            case "customer.subscription.updated" -> handleSubscriptionUpdated(event);
            case "checkout.session.completed" -> log.debug(
                    "webhook_checkout_completed event_id={} — activation handled by reconciliation scheduler",
                    eventId);
            default -> log.debug("webhook_event_unhandled event_id={} type={}", eventId, eventType);
        }
    }

    private void handleSubscriptionEvent(Event event) {
        Optional<StripeObject> objectOpt = event.getDataObjectDeserializer().getObject();
        if (objectOpt.isEmpty()) {
            log.warn("webhook_deserialization_failed event_id={} type={}", event.getId(), event.getType());
            return;
        }

        if (!(objectOpt.get() instanceof com.stripe.model.Subscription stripeSubscription)) {
            log.warn("webhook_unexpected_type event_id={} expected=Subscription", event.getId());
            return;
        }

        String stripeSubId = stripeSubscription.getId();
        subscriptionRepository.findByStripeSubscriptionId(stripeSubId)
                .ifPresentOrElse(
                        sub -> {
                            subscriptionService.syncFromStripe(sub);
                            log.info("webhook_subscription_synced stripe_sub_id={}", stripeSubId);
                        },
                        () -> log.debug("webhook_subscription_not_found stripe_sub_id={}", stripeSubId)
                );
    }

    /**
     * Handles {@code customer.subscription.updated} events.
     *
     * <p>Two paths:
     * <ol>
     *   <li>Deferred downgrade: if {@code pendingPlanId} is set AND the billing period just
     *       renewed (new {@code currentPeriodStart} is after the stored one), apply the downgrade
     *       by promoting {@code pendingPlanId} to {@code planId} and clearing {@code pendingPlanId}.
     *   <li>Upgrade / renewal: resolve the Stripe price ID to a local {@link Plan}, update
     *       {@code planId}, and sync period timestamps.
     * </ol>
     *
     * <p>In both paths, {@code status}, {@code cancelAtPeriodEnd}, {@code currentPeriodStart},
     * and {@code currentPeriodEnd} are always synced from the Stripe payload.
     */
    private void handleSubscriptionUpdated(Event event) {
        Optional<StripeObject> objectOpt = event.getDataObjectDeserializer().getObject();
        if (objectOpt.isEmpty()) {
            log.warn("webhook_deserialization_failed event_id={} type=customer.subscription.updated",
                    event.getId());
            return;
        }

        if (!(objectOpt.get() instanceof com.stripe.model.Subscription stripeSub)) {
            log.warn("webhook_unexpected_type event_id={} expected=Subscription", event.getId());
            return;
        }

        String stripeSubId = stripeSub.getId();
        Optional<Subscription> localSubOpt = subscriptionRepository.findByStripeSubscriptionId(stripeSubId);
        if (localSubOpt.isEmpty()) {
            log.debug("webhook_subscription_not_found stripe_sub_id={}", stripeSubId);
            return;
        }

        Subscription sub = localSubOpt.get();

        // currentPeriodStart / currentPeriodEnd live on the first SubscriptionItem in Stripe SDK v29+
        com.stripe.model.SubscriptionItem firstItem =
                (stripeSub.getItems() != null && !stripeSub.getItems().getData().isEmpty())
                        ? stripeSub.getItems().getData().get(0)
                        : null;

        Instant newPeriodStart = (firstItem != null && firstItem.getCurrentPeriodStart() != null)
                ? Instant.ofEpochSecond(firstItem.getCurrentPeriodStart())
                : null;
        Instant newPeriodEnd = (firstItem != null && firstItem.getCurrentPeriodEnd() != null)
                ? Instant.ofEpochSecond(firstItem.getCurrentPeriodEnd())
                : null;

        // Determine whether the billing period just renewed
        boolean periodRenewed = newPeriodStart != null
                && sub.getCurrentPeriodStart() != null
                && newPeriodStart.isAfter(sub.getCurrentPeriodStart());

        if (sub.getPendingPlanId() != null && periodRenewed) {
            // Apply deferred downgrade: the period just flipped, promote pendingPlanId → planId
            log.info("webhook_subscription_downgrade_applied stripe_sub_id={} pending_plan_id={}",
                    stripeSubId, sub.getPendingPlanId());
            sub.setPlanId(sub.getPendingPlanId());
            sub.setPendingPlanId(null);
        } else {
            // Upgrade or renewal: sync planId from Stripe price ID
            String priceId = (firstItem != null && firstItem.getPrice() != null)
                    ? firstItem.getPrice().getId()
                    : null;

            if (priceId != null) {
                planRepository.findByStripePriceId(priceId).ifPresentOrElse(
                        plan -> {
                            sub.setPlanId(plan.getId());
                            log.info("webhook_subscription_plan_updated stripe_sub_id={} plan={}",
                                    stripeSubId, plan.getSlug());
                        },
                        () -> log.warn("webhook_plan_not_found_for_price stripe_sub_id={} price_id={}",
                                stripeSubId, priceId)
                );
            }
        }

        // Always sync common fields from Stripe payload
        if (stripeSub.getStatus() != null) {
            sub.setStatus(mapStripeStatus(stripeSub.getStatus()));
        }
        sub.setCancelAtPeriodEnd(Boolean.TRUE.equals(stripeSub.getCancelAtPeriodEnd()));
        if (newPeriodStart != null) {
            sub.setCurrentPeriodStart(newPeriodStart);
        }
        if (newPeriodEnd != null) {
            sub.setCurrentPeriodEnd(newPeriodEnd);
        }
        sub.setUpdatedAt(Instant.now());

        subscriptionRepository.save(sub);
        log.info("webhook_subscription_updated stripe_sub_id={}", stripeSubId);
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
