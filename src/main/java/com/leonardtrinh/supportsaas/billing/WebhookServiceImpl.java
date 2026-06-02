package com.leonardtrinh.supportsaas.billing;

import com.leonardtrinh.supportsaas.email.AsyncEmailSender;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.Invoice.Parent;
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
    private final AsyncEmailSender asyncEmailSender;

    public WebhookServiceImpl(
            ProcessedWebhookEventRepository processedWebhookEventRepository,
            SubscriptionRepository subscriptionRepository,
            SubscriptionService subscriptionService,
            AsyncEmailSender asyncEmailSender) {
        this.processedWebhookEventRepository = processedWebhookEventRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionService = subscriptionService;
        this.asyncEmailSender = asyncEmailSender;
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
            case "checkout.session.completed" -> log.debug(
                    "webhook_checkout_completed event_id={} — activation handled by reconciliation scheduler",
                    eventId);
            case "invoice.payment_succeeded" -> handleInvoicePaymentSucceeded(event);
            case "invoice.payment_failed" -> handleInvoicePaymentFailed(event);
            default -> log.debug("webhook_event_unhandled event_id={} type={}", eventId, eventType);
        }
    }

    private void handleInvoicePaymentSucceeded(Event event) {
        Optional<StripeObject> objectOpt = event.getDataObjectDeserializer().getObject();
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
        Optional<StripeObject> objectOpt = event.getDataObjectDeserializer().getObject();
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
