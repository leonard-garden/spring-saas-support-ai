package com.leonardtrinh.supportsaas.billing;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.CustomerSearchResult;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionSchedule;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerSearchParams;
import com.stripe.param.InvoiceListParams;
import com.stripe.param.SubscriptionScheduleCreateParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class StripeServiceImpl implements StripeService {

    private static final Logger log = LoggerFactory.getLogger(StripeServiceImpl.class);

    private final StripeProperties stripeProperties;

    public StripeServiceImpl(StripeProperties stripeProperties) {
        this.stripeProperties = stripeProperties;
    }

    @PostConstruct
    void init() {
        Stripe.apiKey = stripeProperties.secretKey();
    }

    @Override
    public Customer getOrCreateCustomer(String email, UUID businessId) {
        try {
            CustomerSearchParams searchParams = CustomerSearchParams.builder()
                    .setQuery("metadata['businessId']:'" + businessId + "'")
                    .build();
            CustomerSearchResult result = Customer.search(searchParams);
            if (!result.getData().isEmpty()) {
                return result.getData().get(0);
            }
            CustomerCreateParams createParams = CustomerCreateParams.builder()
                    .setEmail(email)
                    .putMetadata("businessId", businessId.toString())
                    .build();
            return Customer.create(createParams);
        } catch (StripeException e) {
            log.warn("stripe_call_failed op={} error={}", "get_or_create_customer", e.getMessage(), e);
            throw new StripeGatewayException("Failed to get or create Stripe customer", e);
        }
    }

    @Override
    public Session createCheckoutSession(String customerId, String priceId,
                                         String successUrl, String cancelUrl,
                                         String idempotencyKey) {
        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setCustomer(customerId)
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setPrice(priceId)
                            .setQuantity(1L)
                            .build())
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl)
                    .build();
            RequestOptions options = RequestOptions.builder()
                    .setIdempotencyKey(idempotencyKey)
                    .build();
            return Session.create(params, options);
        } catch (StripeException e) {
            log.warn("stripe_call_failed op={} error={}", "create_checkout_session", e.getMessage(), e);
            throw new StripeGatewayException("Failed to create checkout session", e);
        }
    }

    @Override
    public Subscription cancelAtPeriodEnd(String subscriptionId, String idempotencyKey) {
        try {
            Subscription subscription = Subscription.retrieve(subscriptionId);
            SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                    .setCancelAtPeriodEnd(true)
                    .build();
            RequestOptions options = RequestOptions.builder()
                    .setIdempotencyKey(idempotencyKey)
                    .build();
            return subscription.update(params, options);
        } catch (StripeException e) {
            log.warn("stripe_call_failed op={} error={}", "cancel_at_period_end", e.getMessage(), e);
            throw new StripeGatewayException("Failed to cancel subscription at period end", e);
        }
    }

    @Override
    public Subscription updateSubscription(String subscriptionId, String newPriceId,
                                            String idempotencyKey) {
        try {
            Subscription subscription = Subscription.retrieve(subscriptionId);
            var items = subscription.getItems().getData();
            if (items.isEmpty()) {
                throw new StripeGatewayException("Subscription " + subscriptionId + " has no items to update", null);
            }
            String itemId = items.get(0).getId();
            SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                    .addItem(SubscriptionUpdateParams.Item.builder()
                            .setId(itemId)
                            .setPrice(newPriceId)
                            .build())
                    .setProrationBehavior(SubscriptionUpdateParams.ProrationBehavior.CREATE_PRORATIONS)
                    .build();
            RequestOptions options = RequestOptions.builder()
                    .setIdempotencyKey(idempotencyKey)
                    .build();
            return subscription.update(params, options);
        } catch (StripeException e) {
            log.warn("stripe_call_failed op={} error={}", "update_subscription", e.getMessage(), e);
            throw new StripeGatewayException("Failed to update subscription", e);
        }
    }

    @Override
    public SubscriptionSchedule scheduleSubscriptionUpdate(String subscriptionId,
                                                            String newPriceId,
                                                            String idempotencyKey) {
        try {
            SubscriptionScheduleCreateParams params = SubscriptionScheduleCreateParams.builder()
                    .setFromSubscription(subscriptionId)
                    .addPhase(SubscriptionScheduleCreateParams.Phase.builder()
                            .addItem(SubscriptionScheduleCreateParams.Phase.Item.builder()
                                    .setPrice(newPriceId)
                                    .build())
                            .build())
                    .setEndBehavior(SubscriptionScheduleCreateParams.EndBehavior.RELEASE)
                    .build();
            RequestOptions options = RequestOptions.builder()
                    .setIdempotencyKey(idempotencyKey)
                    .build();
            return SubscriptionSchedule.create(params, options);
        } catch (StripeException e) {
            log.warn("stripe_call_failed op={} error={}", "schedule_subscription_update", e.getMessage(), e);
            throw new StripeGatewayException("Failed to schedule subscription update", e);
        }
    }

    @Override
    public Subscription retrieveSubscription(String subscriptionId) {
        try {
            return Subscription.retrieve(subscriptionId);
        } catch (StripeException e) {
            log.warn("stripe_call_failed op={} error={}", "retrieve_subscription", e.getMessage(), e);
            throw new StripeGatewayException("Failed to retrieve subscription", e);
        }
    }

    @Override
    public List<InvoiceResponse> listInvoices(String stripeCustomerId) {
        try {
            InvoiceListParams params = InvoiceListParams.builder()
                    .setCustomer(stripeCustomerId)
                    .setLimit(12L)
                    .build();
            return Invoice.list(params).getData().stream()
                    .map(inv -> new InvoiceResponse(
                            inv.getId(),
                            mapInvoiceDate(inv.getCreated()),
                            inv.getDescription() != null ? inv.getDescription() : mapInvoiceDescription(inv),
                            inv.getAmountPaid() / 100.0,
                            mapInvoiceStatus(inv.getStatus()),
                            inv.getInvoicePdf()))
                    .toList();
        } catch (StripeException e) {
            log.warn("stripe_call_failed op={} error={}", "list_invoices", e.getMessage(), e);
            throw new StripeGatewayException("Failed to list invoices", e);
        }
    }

    private String mapInvoiceDate(Long epochSeconds) {
        if (epochSeconds == null) return "";
        return Instant.ofEpochSecond(epochSeconds)
                .atOffset(ZoneOffset.UTC)
                .toLocalDate()
                .toString();
    }

    private String mapInvoiceDescription(Invoice inv) {
        if (inv.getLines() != null && !inv.getLines().getData().isEmpty()) {
            return inv.getLines().getData().get(0).getDescription();
        }
        return "";
    }

    private String mapInvoiceStatus(String stripeStatus) {
        if (stripeStatus == null) return "failed";
        return switch (stripeStatus) {
            case "paid" -> "paid";
            default -> "failed";
        };
    }

    @Override
    public String createPortalSession(String customerId, String returnUrl) {
        try {
            com.stripe.param.billingportal.SessionCreateParams params =
                    com.stripe.param.billingportal.SessionCreateParams.builder()
                            .setCustomer(customerId)
                            .setReturnUrl(returnUrl)
                            .build();
            com.stripe.model.billingportal.Session session =
                    com.stripe.model.billingportal.Session.create(params);
            return session.getUrl();
        } catch (StripeException e) {
            log.warn("stripe_call_failed op={} error={}", "create_portal_session", e.getMessage(), e);
            throw new StripeGatewayException("Failed to create billing portal session", e);
        }
    }

    @Override
    public Event constructWebhookEvent(String payload, String sigHeader) {
        try {
            return Webhook.constructEvent(payload, sigHeader, stripeProperties.webhookSecret());
        } catch (com.stripe.exception.SignatureVerificationException | com.google.gson.JsonSyntaxException e) {
            log.warn("stripe_call_failed op={} error={}", "construct_webhook_event", e.getMessage(), e);
            throw new StripeGatewayException("Failed to construct webhook event", e);
        }
    }
}
