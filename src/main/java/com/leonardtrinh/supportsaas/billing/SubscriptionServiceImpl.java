package com.leonardtrinh.supportsaas.billing;

import com.leonardtrinh.supportsaas.auth.PlanMisconfiguredException;
import com.leonardtrinh.supportsaas.common.ResourceNotFoundException;
import com.leonardtrinh.supportsaas.tenant.Business;
import com.leonardtrinh.supportsaas.tenant.BusinessRepository;
import com.stripe.model.Customer;
import com.stripe.model.checkout.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SubscriptionServiceImpl implements SubscriptionService {

    private static final String PLAN_PRO = "pro";
    private static final String PLAN_FREE = "free";
    private static final int TRIAL_DAYS = 14;

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final BusinessRepository businessRepository;
    private final StripeService stripeService;
    private final String baseUrl;

    public SubscriptionServiceImpl(SubscriptionRepository subscriptionRepository,
                                   PlanRepository planRepository,
                                   BusinessRepository businessRepository,
                                   StripeService stripeService,
                                   @Value("${app.base-url:http://localhost:8081}") String baseUrl) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.businessRepository = businessRepository;
        this.stripeService = stripeService;
        this.baseUrl = baseUrl;
    }

    @Override
    @Transactional
    public Subscription createTrial(UUID businessId) {
        Plan pro = planRepository.findBySlug(PLAN_PRO)
                .orElseThrow(() -> new PlanMisconfiguredException(PLAN_PRO));

        Instant now = Instant.now();
        Instant trialEndsAt = now.plus(TRIAL_DAYS, ChronoUnit.DAYS);

        Subscription sub = new Subscription();
        sub.setBusinessId(businessId);
        sub.setPlanId(pro.getId());
        sub.setStatus(SubscriptionStatus.TRIALING);
        sub.setTrialEndsAt(trialEndsAt);
        sub.setCurrentPeriodStart(now);
        sub.setCurrentPeriodEnd(trialEndsAt);

        return subscriptionRepository.save(sub);
    }

    @Override
    public Optional<Subscription> getCurrentSubscription(UUID businessId) {
        return subscriptionRepository.findActiveByBusinessId(businessId);
    }

    @Override
    public Optional<Plan> getCurrentPlan(UUID businessId) {
        return getCurrentSubscription(businessId)
                .flatMap(sub -> planRepository.findById(sub.getPlanId()));
    }

    @Override
    public boolean isActivePaid(UUID businessId) {
        return getCurrentPlan(businessId)
                .map(plan -> !PLAN_FREE.equals(plan.getSlug()))
                .orElse(false);
    }

    @Override
    public List<Plan> getActivePlans() {
        return planRepository.findAll().stream()
                .filter(Plan::isActive)
                .toList();
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CheckoutResponse startCheckout(UUID businessId, String adminEmail, String planSlug) {
        if (PLAN_FREE.equals(planSlug)) {
            throw new ResourceNotFoundException("Plan", planSlug);
        }

        Plan plan = planRepository.findBySlug(planSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", planSlug));

        subscriptionRepository.findActiveByBusinessId(businessId).ifPresent(sub -> {
            if (sub.getStatus() == SubscriptionStatus.ACTIVE) {
                throw new AlreadySubscribedException();
            }
        });

        Customer customer = stripeService.getOrCreateCustomer(adminEmail, businessId);

        saveStripeCustomerId(businessId, customer.getId());

        String successUrl = baseUrl + "/api/v1/billing/success?session_id={CHECKOUT_SESSION_ID}";
        String cancelUrl = baseUrl + "/api/v1/billing/checkout/cancel";
        String idempotencyKey = businessId + ":checkout:" + LocalDate.now(ZoneOffset.UTC);

        if (plan.getStripePriceId() == null) {
            throw new CannotUpgradeException("This plan is not available for purchase. Please contact support.");
        }

        Session session = stripeService.createCheckoutSession(
                customer.getId(), plan.getStripePriceId(), successUrl, cancelUrl, idempotencyKey);

        return new CheckoutResponse(session.getUrl());
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CancelSubscriptionResponse cancelSubscription(UUID businessId) {
        Subscription sub = subscriptionRepository.findActiveByBusinessId(businessId)
                .orElseThrow(SubscriptionNotCancellableException::new);

        Plan plan = planRepository.findById(sub.getPlanId())
                .orElseThrow(() -> new PlanMisconfiguredException("unknown"));

        if (PLAN_FREE.equals(plan.getSlug())) {
            throw new SubscriptionNotCancellableException();
        }

        if (sub.isCancelAtPeriodEnd()) {
            throw new AlreadyCancelledAtPeriodEndException();
        }

        String stripeSubId = sub.getStripeSubscriptionId();
        if (stripeSubId == null) {
            throw new SubscriptionNotCancellableException();
        }

        String idempotencyKey = businessId + ":cancel:" + LocalDate.now(ZoneOffset.UTC);
        stripeService.cancelAtPeriodEnd(stripeSubId, idempotencyKey);

        return new CancelSubscriptionResponse(true, sub.getCurrentPeriodEnd());
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UpgradeResponse upgradeSubscription(UUID businessId, String planSlug) {
        Subscription sub = subscriptionRepository.findActiveByBusinessId(businessId)
                .orElseThrow(() -> new CannotUpgradeException("No active subscription found."));

        String stripeSubId = sub.getStripeSubscriptionId();
        if (stripeSubId == null) {
            throw new CannotUpgradeException(
                    "Subscription has no Stripe ID — tenant is still on an unpaid trial.");
        }

        Plan targetPlan = planRepository.findBySlug(planSlug)
                .orElseThrow(() -> new com.leonardtrinh.supportsaas.common.ResourceNotFoundException("Plan", planSlug));

        if (targetPlan.getStripePriceId() == null) {
            throw new CannotUpgradeException("Target plan has no Stripe price configured.");
        }

        Plan currentPlan = planRepository.findById(sub.getPlanId())
                .orElseThrow(() -> new com.leonardtrinh.supportsaas.auth.PlanMisconfiguredException("unknown"));

        if (targetPlan.getPriceUsdMonthly().compareTo(currentPlan.getPriceUsdMonthly()) <= 0) {
            throw new CannotUpgradeException(
                    "Target plan price must be higher than current plan price to upgrade.");
        }

        String idempotencyKey = businessId + ":upgrade:" + planSlug + ":" + LocalDate.now(ZoneOffset.UTC);
        stripeService.updateSubscription(stripeSubId, targetPlan.getStripePriceId(), idempotencyKey);

        return UpgradeResponse.of(targetPlan.getName());
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DowngradeResponse downgradeSubscription(UUID businessId, String planSlug) {
        Subscription sub = subscriptionRepository.findActiveByBusinessId(businessId)
                .orElseThrow(() -> new CannotDowngradeException("No active subscription found."));

        String stripeSubId = sub.getStripeSubscriptionId();
        if (stripeSubId == null) {
            throw new CannotDowngradeException(
                    "Subscription has no Stripe ID — tenant is still on an unpaid trial.");
        }

        if (sub.getPendingPlanId() != null) {
            throw new CannotDowngradeException(
                    "A downgrade is already scheduled. Cancel the pending downgrade before scheduling a new one.");
        }

        Plan targetPlan = planRepository.findBySlug(planSlug)
                .orElseThrow(() -> new com.leonardtrinh.supportsaas.common.ResourceNotFoundException("Plan", planSlug));

        if (targetPlan.getStripePriceId() == null) {
            throw new CannotDowngradeException("Target plan has no Stripe price configured.");
        }

        Plan currentPlan = planRepository.findById(sub.getPlanId())
                .orElseThrow(() -> new com.leonardtrinh.supportsaas.auth.PlanMisconfiguredException("unknown"));

        if (targetPlan.getPriceUsdMonthly().compareTo(currentPlan.getPriceUsdMonthly()) >= 0) {
            throw new CannotDowngradeException(
                    "Target plan price must be lower than current plan price to downgrade.");
        }

        String idempotencyKey = businessId + ":downgrade:" + planSlug + ":" + LocalDate.now(ZoneOffset.UTC);
        stripeService.scheduleSubscriptionUpdate(stripeSubId, targetPlan.getStripePriceId(), idempotencyKey);

        savePendingPlanId(businessId, targetPlan.getId());

        return DowngradeResponse.of(targetPlan.getName(), sub.getCurrentPeriodEnd());
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<InvoiceResponse> getInvoices(UUID businessId) {
        java.util.Optional<Subscription> sub = subscriptionRepository.findActiveByBusinessId(businessId);
        if (sub.isEmpty() || sub.get().getStripeCustomerId() == null) {
            return List.of();
        }
        return stripeService.listInvoices(sub.get().getStripeCustomerId());
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String createPortalSession(UUID businessId) {
        Subscription sub = subscriptionRepository.findActiveByBusinessId(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", businessId));

        String stripeCustomerId = sub.getStripeCustomerId();
        if (stripeCustomerId == null) {
            throw new CannotUpgradeException("No billing account on file. Please complete an upgrade first to manage your payment details.");
        }

        String returnUrl = baseUrl.replace("/api/v1", "") + "/billing";
        return stripeService.createPortalSession(stripeCustomerId, returnUrl);
    }

    @Override
    @Transactional
    public void syncFromStripe(Subscription subscription) {
        com.stripe.model.Subscription stripeSubscription =
                stripeService.retrieveSubscription(subscription.getStripeSubscriptionId());

        SubscriptionStatus newStatus = mapStripeStatus(stripeSubscription.getStatus());
        subscription.setStatus(newStatus);
        subscription.setUpdatedAt(Instant.now());
        subscriptionRepository.save(subscription);
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

    @Transactional
    void savePendingPlanId(UUID businessId, UUID pendingPlanId) {
        Subscription sub = subscriptionRepository.findActiveByBusinessId(businessId)
                .orElseThrow(() -> new CannotDowngradeException("Subscription disappeared during downgrade."));
        sub.setPendingPlanId(pendingPlanId);
        sub.setUpdatedAt(Instant.now());
        subscriptionRepository.save(sub);
    }

    @Transactional
    void saveStripeCustomerId(UUID businessId, String customerId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business", businessId));
        business.setStripeCustomerId(customerId);
        businessRepository.save(business);
    }
}
