package com.leonardtrinh.supportsaas.billing;

import com.leonardtrinh.supportsaas.email.AsyncEmailSender;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.Invoice.Parent;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
public class WebhookServiceImpl implements WebhookService {

    private final ProcessedWebhookEventRepository processedWebhookEventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;
    private final AsyncEmailSender asyncEmailSender;
    private final PlanRepository planRepository;
    private final StripeService stripeService;

    public WebhookServiceImpl(
            ProcessedWebhookEventRepository processedWebhookEventRepository,
            SubscriptionRepository subscriptionRepository,
            SubscriptionService subscriptionService,
            AsyncEmailSender asyncEmailSender,
            PlanRepository planRepository,
            StripeService stripeService) {
        this.processedWebhookEventRepository = processedWebhookEventRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionService = subscriptionService;
        this.asyncEmailSender = asyncEmailSender;
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
            case "customer.subscription.created" -> handleSubscriptionEvent(event);
            case "customer.subscription.updated" -> handleSubscriptionUpdated(event);
            case "customer.subscription.deleted" -> handleSubscriptionDeleted(event);
            case "checkout.session.completed" -> handleCheckoutCompleted(event);
            case "invoice.payment_succeeded" -> handleInvoicePaymentSucceeded(event);
            case "invoice.payment_failed" -> handleInvoicePaymentFailed(event);
            default -> log.debug("webhook_event_unhandled event_id={} type={}", eventId, eventType);
        }
    }

    private void handleCheckoutCompleted(Event event) {
        Optional<StripeObject> objectOpt = deserialize(event);
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

        com.stripe.model.SubscriptionItem firstItem =
                (stripeSub.getItems() != null && !stripeSub.getItems().getData().isEmpty())
                        ? stripeSub.getItems().getData().get(0)
                        : null;

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

    private void handleInvoicePaymentSucceeded(Event event) {
        Optional<StripeObject> objectOpt = deserialize(event);
        if (objectOpt.isEmpty()) {
            log.warn("webhook_deserialization_failed event_id={} type={}", event.getId(), event.getType());
            return;
        }
        if (!(objectOpt.get() instanceof Invoice invoice)) {
            log.warn("webhook_unexpected_type event_id={} expected=Invoice", event.getId());
            return;
        }

        Parent parent = invoice.getParent();
        if (parent == null || parent.getSubscriptionDetails() == null) {
            log.debug("webhook_invoice_no_subscription event_id={} — not a subscription invoice", event.getId());
            return;
        }
        String stripeSubId = parent.getSubscriptionDetails().getSubscription();
        subscriptionRepository.findByStripeSubscriptionId(stripeSubId)
                .ifPresentOrElse(
                        sub -> {
                            sub.setStatus(SubscriptionStatus.ACTIVE);
                            if (invoice.getPeriodStart() != null) {
                                sub.setCurrentPeriodStart(Instant.ofEpochSecond(invoice.getPeriodStart()));
                            }
                            if (invoice.getPeriodEnd() != null) {
                                sub.setCurrentPeriodEnd(Instant.ofEpochSecond(invoice.getPeriodEnd()));
                            }
                            sub.setUpdatedAt(Instant.now());
                            subscriptionRepository.save(sub);
                            log.info("webhook_invoice_payment_succeeded stripe_sub_id={}", stripeSubId);
                        },
                        () -> log.debug("webhook_subscription_not_found stripe_sub_id={}", stripeSubId)
                );
    }

    private void handleInvoicePaymentFailed(Event event) {
        Optional<StripeObject> objectOpt = deserialize(event);
        if (objectOpt.isEmpty()) {
            log.warn("webhook_deserialization_failed event_id={} type={}", event.getId(), event.getType());
            return;
        }
        if (!(objectOpt.get() instanceof Invoice invoice)) {
            log.warn("webhook_unexpected_type event_id={} expected=Invoice", event.getId());
            return;
        }

        Parent parent = invoice.getParent();
        if (parent == null || parent.getSubscriptionDetails() == null) {
            log.debug("webhook_invoice_no_subscription event_id={} — not a subscription invoice", event.getId());
            return;
        }
        String stripeSubId = parent.getSubscriptionDetails().getSubscription();
        subscriptionRepository.findByStripeSubscriptionId(stripeSubId)
                .ifPresentOrElse(
                        sub -> {
                            sub.setStatus(SubscriptionStatus.PAST_DUE);
                            sub.setUpdatedAt(Instant.now());
                            subscriptionRepository.save(sub);
                            log.info("webhook_invoice_payment_failed stripe_sub_id={}", stripeSubId);

                            subscriptionRepository.findOwnerEmailByBusinessId(sub.getBusinessId())
                                    .ifPresentOrElse(
                                            ownerEmail -> asyncEmailSender.sendPaymentFailedAsync(ownerEmail),
                                            () -> log.warn("webhook_owner_email_not_found business_id={}", sub.getBusinessId())
                                    );
                        },
                        () -> log.debug("webhook_subscription_not_found stripe_sub_id={}", stripeSubId)
                );
    }

    private void handleSubscriptionEvent(Event event) {
        Optional<StripeObject> objectOpt = deserialize(event);
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
        Optional<StripeObject> objectOpt = deserialize(event);
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
        Optional<StripeObject> objectOpt = deserialize(event);
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

    /**
     * Deserializes the event's data object, falling back to {@code deserializeUnsafe()} when the
     * Stripe-SDK API version doesn't match the webhook's API version (common in local dev with the
     * Stripe CLI, which forwards events using the dashboard account version).
     */
    private Optional<StripeObject> deserialize(Event event) {
        Optional<StripeObject> result = event.getDataObjectDeserializer().getObject();
        if (result.isPresent()) {
            return result;
        }
        try {
            return Optional.of(event.getDataObjectDeserializer().deserializeUnsafe());
        } catch (EventDataObjectDeserializationException e) {
            log.warn("webhook_deserialization_failed event_id={} type={} err={}",
                    event.getId(), event.getType(), e.getMessage());
            return Optional.empty();
        }
    }
}
