package com.leonardtrinh.supportsaas.billing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionService {
    Subscription createTrial(UUID businessId);
    Optional<Subscription> getCurrentSubscription(UUID businessId);
    Optional<Plan> getCurrentPlan(UUID businessId);
    boolean isActivePaid(UUID businessId);
    List<Plan> getActivePlans();
    CheckoutResponse startCheckout(UUID businessId, String adminEmail, String planSlug);
    CancelSubscriptionResponse cancelSubscription(UUID businessId);

    /**
     * Reconcile a single subscription's status against Stripe.
     * Called by StripeReconciliationScheduler when a status mismatch is detected.
     */
    void syncFromStripe(Subscription subscription);
}
