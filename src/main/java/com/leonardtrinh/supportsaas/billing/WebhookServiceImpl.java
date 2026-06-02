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
                 "customer.subscription.updated" -> handleSubscriptionEvent(event);
            case "customer.subscription.deleted" -> handleSubscriptionDeleted(event);
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

    private void handleSubscriptionDeleted(Event event) {
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
        Optional<Subscription> localSubOpt = subscriptionRepository.findByStripeSubscriptionId(stripeSubId);
        if (localSubOpt.isEmpty()) {
            log.debug("webhook_subscription_not_found stripe_sub_id={}", stripeSubId);
            return;
        }

        Plan freePlan = planRepository.findBySlug("free").orElseThrow(
                () -> new IllegalStateException("Free plan not found — database seed missing"));

        Subscription sub = localSubOpt.get();
        sub.setPlanId(freePlan.getId());
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setCancelAtPeriodEnd(false);
        sub.setStripeSubscriptionId(null);
        sub.setUpdatedAt(Instant.now());

        subscriptionRepository.save(sub);
        log.info("webhook_subscription_deleted_downgraded stripe_sub_id={} business_id={}",
                stripeSubId, sub.getBusinessId());
    }
}
