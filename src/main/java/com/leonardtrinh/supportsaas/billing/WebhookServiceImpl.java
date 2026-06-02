package com.leonardtrinh.supportsaas.billing;

import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
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
    private final StripeService stripeService;

    public WebhookServiceImpl(
            ProcessedWebhookEventRepository processedWebhookEventRepository,
            SubscriptionRepository subscriptionRepository,
            SubscriptionService subscriptionService,
            PlanRepository planRepository,
            StripeService stripeService) {
        this.processedWebhookEventRepository = processedWebhookEventRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionService = subscriptionService;
        this.planRepository = planRepository;
        this.stripeService = stripeService;
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
                 "customer.subscription.updated",
                 "customer.subscription.deleted" -> handleSubscriptionEvent(event);
            case "checkout.session.completed" -> handleCheckoutCompleted(event);
            default -> log.debug("webhook_event_unhandled event_id={} type={}", eventId, eventType);
        }
    }

    private void handleCheckoutCompleted(Event event) {
        Optional<StripeObject> objectOpt = event.getDataObjectDeserializer().getObject();
        if (objectOpt.isEmpty()) {
            log.warn("webhook_checkout_deserialization_failed event_id={}", event.getId());
            return;
        }

        if (!(objectOpt.get() instanceof Session session)) {
            log.warn("webhook_checkout_unexpected_type event_id={} expected=Session", event.getId());
            return;
        }

        String stripeSubId = session.getSubscription();
        String stripeCustomerId = session.getCustomer();

        if (stripeSubId == null || stripeCustomerId == null) {
            log.warn("webhook_checkout_missing_ids event_id={} stripeSubId={} stripeCustomerId={}",
                    event.getId(), stripeSubId, stripeCustomerId);
            return;
        }

        Subscription sub = subscriptionRepository.findByStripeCustomerId(stripeCustomerId)
                .orElse(null);
        if (sub == null) {
            log.warn("webhook_checkout_subscription_not_found event_id={} stripe_customer_id={}",
                    event.getId(), stripeCustomerId);
            return;
        }

        com.stripe.model.Subscription stripeSub = stripeService.retrieveSubscription(stripeSubId);

        // In Stripe SDK v29+, period and price data live on SubscriptionItem, not Subscription
        com.stripe.model.SubscriptionItem firstItem =
                (stripeSub.getItems() != null && !stripeSub.getItems().getData().isEmpty())
                        ? stripeSub.getItems().getData().get(0)
                        : null;

        // Resolve planId from Stripe price ID — fall back to existing planId if not matched
        if (firstItem != null && firstItem.getPrice() != null) {
            String stripePriceId = firstItem.getPrice().getId();
            planRepository.findByStripePriceId(stripePriceId)
                    .ifPresent(plan -> sub.setPlanId(plan.getId()));
        }

        sub.setStripeSubscriptionId(stripeSubId);
        sub.setStripeCustomerId(stripeCustomerId);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        if (firstItem != null && firstItem.getCurrentPeriodStart() != null) {
            sub.setCurrentPeriodStart(Instant.ofEpochSecond(firstItem.getCurrentPeriodStart()));
        }
        if (firstItem != null && firstItem.getCurrentPeriodEnd() != null) {
            sub.setCurrentPeriodEnd(Instant.ofEpochSecond(firstItem.getCurrentPeriodEnd()));
        }
        sub.setUpdatedAt(Instant.now());

        subscriptionRepository.save(sub);

        log.info("webhook_checkout_activated event_id={} stripe_sub_id={} stripe_customer_id={}",
                event.getId(), stripeSubId, stripeCustomerId);
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
}
