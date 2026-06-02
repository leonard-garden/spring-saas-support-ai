package com.leonardtrinh.supportsaas.billing;

import com.stripe.model.Event;

public interface WebhookService {

    /**
     * Handle a verified Stripe webhook event with idempotency protection.
     * Already-processed events are silently skipped.
     *
     * @param event the verified Stripe event
     */
    void handle(Event event);
}
