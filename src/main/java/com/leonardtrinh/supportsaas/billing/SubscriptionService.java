package com.leonardtrinh.supportsaas.billing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionService {
    List<InvoiceResponse> getInvoices(UUID businessId);

    Subscription createTrial(UUID businessId);
    Optional<Subscription> getCurrentSubscription(UUID businessId);
    Optional<Plan> getCurrentPlan(UUID businessId);
    boolean isActivePaid(UUID businessId);
    List<Plan> getActivePlans();
    CheckoutResponse startCheckout(UUID businessId, String adminEmail, String planSlug);
    CancelSubscriptionResponse cancelSubscription(UUID businessId);

    /**
     * Immediately upgrade the tenant's subscription to a higher-priced plan with proration.
     * Calls Stripe to update the subscription in real time; the DB record is updated
     * asynchronously via the {@code customer.subscription.updated} webhook.
     *
     * @throws CannotUpgradeException if the tenant has no Stripe subscription (unpaid trial),
     *                                 or if the target plan price is not higher than the current plan.
     */
    UpgradeResponse upgradeSubscription(UUID businessId, String planSlug);

    /**
     * Schedule a deferred downgrade to a lower-priced plan at the current period end.
     * Creates a Stripe SubscriptionSchedule so the price change applies automatically
     * when the billing cycle closes; sets {@code pendingPlanId} on the local record
     * immediately so the UI can show "downgrade scheduled".
     *
     * @throws CannotDowngradeException if the tenant has no Stripe subscription (unpaid trial),
     *                                   if the target plan price is not lower than the current plan,
     *                                   or if a downgrade is already pending.
     */
    DowngradeResponse downgradeSubscription(UUID businessId, String planSlug);

    /**
     * Reconcile a single subscription's status against Stripe.
     * Called by StripeReconciliationScheduler when a status mismatch is detected.
     */
    void syncFromStripe(Subscription subscription);
}
