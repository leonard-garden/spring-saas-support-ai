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

        Session session = stripeService.createCheckoutSession(
                customer.getId(), plan.getStripePriceId(), successUrl, cancelUrl, idempotencyKey);

        return new CheckoutResponse(session.getUrl());
    }

    @Transactional
    void saveStripeCustomerId(UUID businessId, String customerId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business", businessId));
        business.setStripeCustomerId(customerId);
        businessRepository.save(business);
    }
}
