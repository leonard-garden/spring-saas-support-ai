package com.leonardtrinh.supportsaas.billing;

import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionSchedule;
import com.stripe.model.checkout.Session;

import java.util.List;
import java.util.UUID;

public interface StripeService {

    Customer getOrCreateCustomer(String email, UUID businessId);

    Session createCheckoutSession(String customerId, String priceId,
                                  String successUrl, String cancelUrl,
                                  String idempotencyKey);

    Subscription cancelAtPeriodEnd(String subscriptionId, String idempotencyKey);

    Subscription updateSubscription(String subscriptionId, String newPriceId,
                                    String idempotencyKey);

    SubscriptionSchedule scheduleSubscriptionUpdate(String subscriptionId,
                                                    String newPriceId,
                                                    String idempotencyKey);

    Subscription retrieveSubscription(String subscriptionId);

    Event constructWebhookEvent(String payload, String sigHeader);

    List<InvoiceResponse> listInvoices(String stripeCustomerId);
}
