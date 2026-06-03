package com.leonardtrinh.supportsaas.billing;

import com.leonardtrinh.supportsaas.auth.PlanMisconfiguredException;
import com.leonardtrinh.supportsaas.common.ResourceNotFoundException;
import com.leonardtrinh.supportsaas.tenant.Business;
import com.leonardtrinh.supportsaas.tenant.BusinessRepository;
import com.stripe.model.Customer;
import com.stripe.model.SubscriptionSchedule;
import com.stripe.model.checkout.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private StripeService stripeService;

    private SubscriptionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionServiceImpl(subscriptionRepository, planRepository,
                businessRepository, stripeService, "http://localhost:8081");
    }

    @Test
    @DisplayName("createTrial saves a TRIALING subscription when pro plan exists")
    void createTrial_withValidProPlan_savesTrialingSubscription() {
        UUID businessId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        Plan pro = planWith(planId, "pro");
        when(planRepository.findBySlug("pro")).thenReturn(Optional.of(pro));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        Subscription result = service.createTrial(businessId);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        Subscription saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo(SubscriptionStatus.TRIALING);
        assertThat(saved.getBusinessId()).isEqualTo(businessId);
        assertThat(saved.getPlanId()).isEqualTo(planId);
        assertThat(saved.getTrialEndsAt()).isNotNull();
        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.TRIALING);
    }

    @Test
    @DisplayName("createTrial throws PlanMisconfiguredException when pro plan is missing")
    void createTrial_withMissingProPlan_throwsPlanMisconfiguredException() {
        when(planRepository.findBySlug("pro")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createTrial(UUID.randomUUID()))
                .isInstanceOf(PlanMisconfiguredException.class);
    }

    @Test
    @DisplayName("getCurrentSubscription returns subscription when an active one exists")
    void getCurrentSubscription_whenActiveExists_returnsSubscription() {
        UUID businessId = UUID.randomUUID();
        Subscription sub = new Subscription();
        when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.of(sub));

        Optional<Subscription> result = service.getCurrentSubscription(businessId);

        assertThat(result).isPresent().contains(sub);
    }

    @Test
    @DisplayName("getCurrentSubscription returns empty when no active subscription exists")
    void getCurrentSubscription_whenNone_returnsEmpty() {
        UUID businessId = UUID.randomUUID();
        when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.empty());

        Optional<Subscription> result = service.getCurrentSubscription(businessId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getCurrentPlan returns plan when subscription and plan both exist")
    void getCurrentPlan_whenSubscriptionAndPlanExist_returnsPlan() {
        UUID businessId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        Subscription sub = subscriptionWith(planId);
        Plan plan = planWith(planId, "pro");

        when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.of(sub));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

        Optional<Plan> result = service.getCurrentPlan(businessId);

        assertThat(result).isPresent().contains(plan);
    }

    @Test
    @DisplayName("getCurrentPlan returns empty when no active subscription exists")
    void getCurrentPlan_whenNoSubscription_returnsEmpty() {
        UUID businessId = UUID.randomUUID();
        when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.empty());

        Optional<Plan> result = service.getCurrentPlan(businessId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("isActivePaid returns true for an ACTIVE subscription on a non-free plan")
    void isActivePaid_whenActiveNonFreePlan_returnsTrue() {
        UUID businessId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        Subscription sub = subscriptionWith(planId);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        Plan plan = planWith(planId, "pro");

        when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.of(sub));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

        assertThat(service.isActivePaid(businessId)).isTrue();
    }

    @Test
    @DisplayName("isActivePaid returns false for a TRIALING subscription on the free plan")
    void isActivePaid_whenFreePlan_returnsFalse() {
        UUID businessId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        Subscription sub = subscriptionWith(planId);
        sub.setStatus(SubscriptionStatus.TRIALING);
        Plan plan = planWith(planId, "free");

        when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.of(sub));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

        assertThat(service.isActivePaid(businessId)).isFalse();
    }

    @Test
    @DisplayName("isActivePaid returns false when no active subscription exists")
    void isActivePaid_whenNoSubscription_returnsFalse() {
        UUID businessId = UUID.randomUUID();
        when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.empty());

        assertThat(service.isActivePaid(businessId)).isFalse();
    }

    // --- checkout tests ---

    @Test
    @DisplayName("startCheckout with free plan slug throws ResourceNotFoundException")
    void startCheckout_freePlan_throwsResourceNotFound() {
        UUID businessId = UUID.randomUUID();

        assertThatThrownBy(() -> service.startCheckout(businessId, "admin@test.com", "free"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("free");
    }

    @Test
    @DisplayName("startCheckout with unknown plan slug throws ResourceNotFoundException")
    void startCheckout_planNotFound_throwsResourceNotFound() {
        UUID businessId = UUID.randomUUID();
        when(planRepository.findBySlug("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startCheckout(businessId, "admin@test.com", "unknown"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    @DisplayName("startCheckout when subscription is ACTIVE throws AlreadySubscribedException")
    void startCheckout_alreadyActive_throwsAlreadySubscribed() {
        UUID businessId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        Plan plan = planWith(planId, "starter", "price_123");
        when(planRepository.findBySlug("starter")).thenReturn(Optional.of(plan));

        Subscription active = new Subscription();
        active.setStatus(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.startCheckout(businessId, "admin@test.com", "starter"))
                .isInstanceOf(AlreadySubscribedException.class);
    }

    @Test
    @DisplayName("startCheckout when subscription is TRIALING proceeds to create checkout")
    void startCheckout_trialing_createsCheckout() {
        UUID businessId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        Plan plan = planWith(planId, "starter", "price_123");
        when(planRepository.findBySlug("starter")).thenReturn(Optional.of(plan));

        Subscription trialing = new Subscription();
        trialing.setStatus(SubscriptionStatus.TRIALING);
        when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.of(trialing));

        Business business = new Business();
        when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
        when(businessRepository.save(any(Business.class))).thenReturn(business);

        Customer customer = new Customer();
        ReflectionTestUtils.setField(customer, "id", "cus_abc");
        when(stripeService.getOrCreateCustomer(anyString(), any(UUID.class))).thenReturn(customer);

        Session session = new Session();
        ReflectionTestUtils.setField(session, "url", "https://checkout.stripe.com/pay/cs_test_trialing");
        when(stripeService.createCheckoutSession(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(session);

        CheckoutResponse response = service.startCheckout(businessId, "admin@test.com", "starter");

        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.stripe.com/pay/cs_test_trialing");
    }

    @Test
    @DisplayName("startCheckout with no subscription proceeds to create checkout")
    void startCheckout_noSubscription_createsCheckout() {
        UUID businessId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        Plan plan = planWith(planId, "pro", "price_pro");
        when(planRepository.findBySlug("pro")).thenReturn(Optional.of(plan));
        when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.empty());

        Business business = new Business();
        when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
        when(businessRepository.save(any(Business.class))).thenReturn(business);

        Customer customer = new Customer();
        ReflectionTestUtils.setField(customer, "id", "cus_xyz");
        when(stripeService.getOrCreateCustomer(anyString(), any(UUID.class))).thenReturn(customer);

        Session session = new Session();
        ReflectionTestUtils.setField(session, "url", "https://checkout.stripe.com/pay/cs_test_new");
        when(stripeService.createCheckoutSession(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(session);

        CheckoutResponse response = service.startCheckout(businessId, "admin@test.com", "pro");

        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.stripe.com/pay/cs_test_new");
    }

    // --- cancel tests ---

    @Test
    @DisplayName("cancelSubscription throws SubscriptionNotCancellableException when no active subscription")
    void cancelSubscription_noActiveSubscription_throwsNotCancellable() {
        UUID businessId = UUID.randomUUID();
        when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelSubscription(businessId))
                .isInstanceOf(SubscriptionNotCancellableException.class);
    }

    @Test
    @DisplayName("cancelSubscription throws SubscriptionNotCancellableException when plan is Free")
    void cancelSubscription_freePlan_throwsNotCancellable() {
        UUID businessId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        Subscription sub = subscriptionWith(planId);
        sub.setStripeSubscriptionId("sub_123");
        Plan free = planWith(planId, "free");

        when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.of(sub));
        when(planRepository.findById(planId)).thenReturn(Optional.of(free));

        assertThatThrownBy(() -> service.cancelSubscription(businessId))
                .isInstanceOf(SubscriptionNotCancellableException.class);
    }

    @Test
    @DisplayName("cancelSubscription throws AlreadyCancelledAtPeriodEndException when already scheduled to cancel")
    void cancelSubscription_alreadyCancelledAtPeriodEnd_throwsAlreadyCancelled() {
        UUID businessId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        Subscription sub = subscriptionWith(planId);
        sub.setStripeSubscriptionId("sub_123");
        sub.setCancelAtPeriodEnd(true);
        Plan pro = planWith(planId, "pro");

        when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.of(sub));
        when(planRepository.findById(planId)).thenReturn(Optional.of(pro));

        assertThatThrownBy(() -> service.cancelSubscription(businessId))
                .isInstanceOf(AlreadyCancelledAtPeriodEndException.class);
    }

    @Test
    @DisplayName("cancelSubscription throws SubscriptionNotCancellableException when stripeSubscriptionId is null")
    void cancelSubscription_noStripeSubId_throwsNotCancellable() {
        UUID businessId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        Subscription sub = subscriptionWith(planId);
        Plan pro = planWith(planId, "pro");

        when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.of(sub));
        when(planRepository.findById(planId)).thenReturn(Optional.of(pro));

        assertThatThrownBy(() -> service.cancelSubscription(businessId))
                .isInstanceOf(SubscriptionNotCancellableException.class);
    }

    @Test
    @DisplayName("cancelSubscription calls StripeService and returns cancelAtPeriodEnd=true with currentPeriodEnd")
    void cancelSubscription_happyPath_callsStripeAndReturnsResponse() {
        UUID businessId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        Instant periodEnd = Instant.parse("2026-07-01T00:00:00Z");

        Subscription sub = subscriptionWith(planId);
        sub.setStripeSubscriptionId("sub_abc");
        sub.setCurrentPeriodEnd(periodEnd);
        Plan pro = planWith(planId, "pro");

        when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.of(sub));
        when(planRepository.findById(planId)).thenReturn(Optional.of(pro));
        when(stripeService.cancelAtPeriodEnd(anyString(), anyString())).thenReturn(null);

        CancelSubscriptionResponse response = service.cancelSubscription(businessId);

        assertThat(response.cancelAtPeriodEnd()).isTrue();
        assertThat(response.currentPeriodEnd()).isEqualTo(periodEnd);
        verify(stripeService).cancelAtPeriodEnd(anyString(), anyString());
    }

    @Test
    @DisplayName("createTrial sets trialEndsAt approximately 14 days from now")
    void createTrial_setsTrialEndsAt14DaysFromNow() {
        UUID businessId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        Plan pro = planWith(planId, "pro");
        when(planRepository.findBySlug("pro")).thenReturn(Optional.of(pro));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant before = Instant.now().plus(14, ChronoUnit.DAYS).minusSeconds(5);
        Subscription result = service.createTrial(businessId);
        Instant after = Instant.now().plus(14, ChronoUnit.DAYS).plusSeconds(5);

        assertThat(result.getTrialEndsAt()).isAfter(before).isBefore(after);
        assertThat(result.getCurrentPeriodStart()).isNotNull();
        assertThat(result.getCurrentPeriodEnd()).isNotNull();
    }

    // --- upgrade tests ---

    @Test
    @DisplayName("upgradeSubscription throws CannotUpgradeException when no active subscription")
    void upgradeSubscription_noActiveSubscription_throwsCannotUpgrade() {
        UUID businessId = UUID.randomUUID();
        when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upgradeSubscription(businessId, "pro"))
                .isInstanceOf(CannotUpgradeException.class)
                .hasMessageContaining("No active subscription");
    }

    @Test
    @DisplayName("upgradeSubscription throws CannotUpgradeException when stripeSubscriptionId is null (unpaid trial)")
    void upgradeSubscription_noStripeSubId_throwsCannotUpgrade() {
        UUID businessId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        Subscription sub = subscriptionWith(planId);
        // stripeSubscriptionId is null by default
        when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.of(sub));

        assertThatThrownBy(() -> service.upgradeSubscription(businessId, "pro"))
                .isInstanceOf(CannotUpgradeException.class)
                .hasMessageContaining("unpaid trial");
    }

    @Test
    @DisplayName("upgradeSubscription throws ResourceNotFoundException when target plan slug does not exist")
    void upgradeSubscription_unknownPlanSlug_throwsResourceNotFound() {
        UUID businessId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        Subscription sub = subscriptionWith(planId);
        sub.setStripeSubscriptionId("sub_123");
        when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.of(sub));
        when(planRepository.findBySlug("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upgradeSubscription(businessId, "unknown"))
                .isInstanceOf(com.leonardtrinh.supportsaas.common.ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("upgradeSubscription throws CannotUpgradeException when target plan price equals current plan price")
    void upgradeSubscription_samePricePlan_throwsCannotUpgrade() {
        UUID businessId = UUID.randomUUID();
        UUID currentPlanId = UUID.randomUUID();
        UUID targetPlanId = UUID.randomUUID();

        Subscription sub = subscriptionWith(currentPlanId);
        sub.setStripeSubscriptionId("sub_123");

        Plan currentPlan = planWithPrice(currentPlanId, "starter", "price_starter", new java.math.BigDecimal("29.00"));
        Plan targetPlan = planWithPrice(targetPlanId, "starter2", "price_starter2", new java.math.BigDecimal("29.00"));

        when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.of(sub));
        when(planRepository.findBySlug("starter2")).thenReturn(Optional.of(targetPlan));
        when(planRepository.findById(currentPlanId)).thenReturn(Optional.of(currentPlan));

        assertThatThrownBy(() -> service.upgradeSubscription(businessId, "starter2"))
                .isInstanceOf(CannotUpgradeException.class)
                .hasMessageContaining("higher than current plan price");
    }

    @Test
    @DisplayName("upgradeSubscription throws CannotUpgradeException when target plan price is lower than current plan price")
    void upgradeSubscription_lowerPricePlan_throwsCannotUpgrade() {
        UUID businessId = UUID.randomUUID();
        UUID currentPlanId = UUID.randomUUID();
        UUID targetPlanId = UUID.randomUUID();

        Subscription sub = subscriptionWith(currentPlanId);
        sub.setStripeSubscriptionId("sub_abc");

        Plan currentPlan = planWithPrice(currentPlanId, "pro", "price_pro", new java.math.BigDecimal("99.00"));
        Plan targetPlan = planWithPrice(targetPlanId, "starter", "price_starter", new java.math.BigDecimal("29.00"));

        when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.of(sub));
        when(planRepository.findBySlug("starter")).thenReturn(Optional.of(targetPlan));
        when(planRepository.findById(currentPlanId)).thenReturn(Optional.of(currentPlan));

        assertThatThrownBy(() -> service.upgradeSubscription(businessId, "starter"))
                .isInstanceOf(CannotUpgradeException.class)
                .hasMessageContaining("higher than current plan price");
    }

    @Test
    @DisplayName("upgradeSubscription calls StripeService.updateSubscription and returns UpgradeResponse on happy path")
    void upgradeSubscription_happyPath_callsStripeAndReturnsResponse() {
        UUID businessId = UUID.randomUUID();
        UUID currentPlanId = UUID.randomUUID();
        UUID targetPlanId = UUID.randomUUID();

        Subscription sub = subscriptionWith(currentPlanId);
        sub.setStripeSubscriptionId("sub_upgrade_me");

        Plan currentPlan = planWithPrice(currentPlanId, "starter", "price_starter", new java.math.BigDecimal("29.00"));
        Plan targetPlan = planWithPrice(targetPlanId, "pro", "price_pro", new java.math.BigDecimal("99.00"));
        ReflectionTestUtils.setField(targetPlan, "name", "Pro");

        com.stripe.model.Subscription stripeSubscription = new com.stripe.model.Subscription();
        when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.of(sub));
        when(planRepository.findBySlug("pro")).thenReturn(Optional.of(targetPlan));
        when(planRepository.findById(currentPlanId)).thenReturn(Optional.of(currentPlan));
        when(stripeService.updateSubscription(anyString(), anyString(), anyString())).thenReturn(stripeSubscription);

        UpgradeResponse response = service.upgradeSubscription(businessId, "pro");

        assertThat(response.targetPlan()).isEqualTo("Pro");
        assertThat(response.message()).isNotBlank();
        verify(stripeService).updateSubscription(eq("sub_upgrade_me"), eq("price_pro"), anyString());
    }

    // --- downgrade tests ---

    @Test
    @DisplayName("downgradeSubscription throws CannotDowngradeException when no active subscription")
    void downgradeSubscription_noActiveSubscription_throwsCannotDowngrade() {
        UUID businessId = UUID.randomUUID();
        when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.downgradeSubscription(businessId, "starter"))
                .isInstanceOf(CannotDowngradeException.class)
                .hasMessageContaining("No active subscription");
    }

    @Test
    @DisplayName("downgradeSubscription throws CannotDowngradeException when stripeSubscriptionId is null (unpaid trial)")
    void downgradeSubscription_noStripeSubId_throwsCannotDowngrade() {
        UUID businessId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        Subscription sub = subscriptionWith(planId);
        // stripeSubscriptionId is null by default
        when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.of(sub));

        assertThatThrownBy(() -> service.downgradeSubscription(businessId, "starter"))
                .isInstanceOf(CannotDowngradeException.class)
                .hasMessageContaining("unpaid trial");
    }

    @Test
    @DisplayName("downgradeSubscription throws CannotDowngradeException when a downgrade is already pending")
    void downgradeSubscription_pendingPlanAlreadySet_throwsCannotDowngrade() {
        UUID businessId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        Subscription sub = subscriptionWith(planId);
        sub.setStripeSubscriptionId("sub_abc");
        sub.setPendingPlanId(UUID.randomUUID());
        when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.of(sub));

        assertThatThrownBy(() -> service.downgradeSubscription(businessId, "starter"))
                .isInstanceOf(CannotDowngradeException.class)
                .hasMessageContaining("already scheduled");
    }

    @Test
    @DisplayName("downgradeSubscription throws ResourceNotFoundException when target plan slug does not exist")
    void downgradeSubscription_unknownPlanSlug_throwsResourceNotFound() {
        UUID businessId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        Subscription sub = subscriptionWith(planId);
        sub.setStripeSubscriptionId("sub_abc");
        when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.of(sub));
        when(planRepository.findBySlug("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.downgradeSubscription(businessId, "ghost"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("downgradeSubscription throws CannotDowngradeException when target plan price equals current plan price")
    void downgradeSubscription_samePricePlan_throwsCannotDowngrade() {
        UUID businessId = UUID.randomUUID();
        UUID currentPlanId = UUID.randomUUID();
        UUID targetPlanId = UUID.randomUUID();

        Subscription sub = subscriptionWith(currentPlanId);
        sub.setStripeSubscriptionId("sub_abc");

        Plan currentPlan = planWithPrice(currentPlanId, "pro", "price_pro", new BigDecimal("99.00"));
        Plan targetPlan  = planWithPrice(targetPlanId, "pro2", "price_pro2", new BigDecimal("99.00"));

        when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.of(sub));
        when(planRepository.findBySlug("pro2")).thenReturn(Optional.of(targetPlan));
        when(planRepository.findById(currentPlanId)).thenReturn(Optional.of(currentPlan));

        assertThatThrownBy(() -> service.downgradeSubscription(businessId, "pro2"))
                .isInstanceOf(CannotDowngradeException.class)
                .hasMessageContaining("lower than current plan price");
    }

    @Test
    @DisplayName("downgradeSubscription throws CannotDowngradeException when target plan price is higher than current plan price")
    void downgradeSubscription_higherPricePlan_throwsCannotDowngrade() {
        UUID businessId = UUID.randomUUID();
        UUID currentPlanId = UUID.randomUUID();
        UUID targetPlanId = UUID.randomUUID();

        Subscription sub = subscriptionWith(currentPlanId);
        sub.setStripeSubscriptionId("sub_abc");

        Plan currentPlan = planWithPrice(currentPlanId, "starter", "price_starter", new BigDecimal("29.00"));
        Plan targetPlan  = planWithPrice(targetPlanId, "pro",     "price_pro",     new BigDecimal("99.00"));

        when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.of(sub));
        when(planRepository.findBySlug("pro")).thenReturn(Optional.of(targetPlan));
        when(planRepository.findById(currentPlanId)).thenReturn(Optional.of(currentPlan));

        assertThatThrownBy(() -> service.downgradeSubscription(businessId, "pro"))
                .isInstanceOf(CannotDowngradeException.class)
                .hasMessageContaining("lower than current plan price");
    }

    @Test
    @DisplayName("downgradeSubscription calls StripeService.scheduleSubscriptionUpdate, sets pendingPlanId, and returns DowngradeResponse")
    void downgradeSubscription_happyPath_schedulesAndReturnsPendingPlan() {
        UUID businessId = UUID.randomUUID();
        UUID currentPlanId = UUID.randomUUID();
        UUID targetPlanId = UUID.randomUUID();
        Instant periodEnd = Instant.parse("2026-07-01T00:00:00Z");

        Subscription sub = subscriptionWith(currentPlanId);
        sub.setStripeSubscriptionId("sub_downgrade_me");
        sub.setCurrentPeriodEnd(periodEnd);

        Plan currentPlan = planWithPrice(currentPlanId, "pro",     "price_pro",     new BigDecimal("99.00"));
        Plan targetPlan  = planWithPrice(targetPlanId, "starter", "price_starter", new BigDecimal("29.00"));
        ReflectionTestUtils.setField(targetPlan, "name", "Starter");

        SubscriptionSchedule schedule = new SubscriptionSchedule();
        when(subscriptionRepository.findActiveByBusinessId(businessId))
                .thenReturn(Optional.of(sub));
        when(planRepository.findBySlug("starter")).thenReturn(Optional.of(targetPlan));
        when(planRepository.findById(currentPlanId)).thenReturn(Optional.of(currentPlan));
        when(stripeService.scheduleSubscriptionUpdate(anyString(), anyString(), anyString()))
                .thenReturn(schedule);
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        DowngradeResponse response = service.downgradeSubscription(businessId, "starter");

        assertThat(response.targetPlan()).isEqualTo("Starter");
        assertThat(response.effectiveAt()).isEqualTo(periodEnd);
        assertThat(response.message()).isNotBlank();
        verify(stripeService).scheduleSubscriptionUpdate(eq("sub_downgrade_me"), eq("price_starter"), anyString());

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getPendingPlanId()).isEqualTo(targetPlanId);
    }

    // --- invoice tests ---

    @Test
    @DisplayName("getInvoices returns list when stripeCustomerId present")
    void getInvoices_withStripeCustomerId_returnsInvoiceList() {
        UUID businessId = UUID.randomUUID();
        Subscription sub = new Subscription();
        sub.setStripeCustomerId("cus_test123");
        when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.of(sub));
        List<InvoiceResponse> mockInvoices = List.of(
                new InvoiceResponse("in_001", "2026-05-01", "Pro Plan", 99.0, "paid", "https://invoice.stripe.com/pdf1"));
        when(stripeService.listInvoices("cus_test123")).thenReturn(mockInvoices);

        List<InvoiceResponse> result = service.getInvoices(businessId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("in_001");
        assertThat(result.get(0).status()).isEqualTo("paid");
        verify(stripeService).listInvoices("cus_test123");
    }

    @Test
    @DisplayName("getInvoices returns empty list when no active subscription")
    void getInvoices_withNoSubscription_returnsEmptyList() {
        UUID businessId = UUID.randomUUID();
        when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.empty());

        List<InvoiceResponse> result = service.getInvoices(businessId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getInvoices returns empty list when stripeCustomerId is null")
    void getInvoices_withNullStripeCustomerId_returnsEmptyList() {
        UUID businessId = UUID.randomUUID();
        Subscription sub = new Subscription();
        when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.of(sub));

        List<InvoiceResponse> result = service.getInvoices(businessId);

        assertThat(result).isEmpty();
    }

    // --- helpers ---

    private Subscription subscriptionWith(UUID planId) {
        Subscription sub = new Subscription();
        sub.setPlanId(planId);
        return sub;
    }

    private Plan planWith(UUID id, String slug) {
        Plan plan = new Plan();
        ReflectionTestUtils.setField(plan, "id", id);
        ReflectionTestUtils.setField(plan, "slug", slug);
        return plan;
    }

    private Plan planWith(UUID id, String slug, String stripePriceId) {
        Plan plan = new Plan();
        ReflectionTestUtils.setField(plan, "id", id);
        ReflectionTestUtils.setField(plan, "slug", slug);
        ReflectionTestUtils.setField(plan, "stripePriceId", stripePriceId);
        return plan;
    }

    private Plan planWithPrice(UUID id, String slug, String stripePriceId, java.math.BigDecimal price) {
        Plan plan = new Plan();
        ReflectionTestUtils.setField(plan, "id", id);
        ReflectionTestUtils.setField(plan, "slug", slug);
        ReflectionTestUtils.setField(plan, "stripePriceId", stripePriceId);
        ReflectionTestUtils.setField(plan, "priceUsdMonthly", price);
        return plan;
    }
}
