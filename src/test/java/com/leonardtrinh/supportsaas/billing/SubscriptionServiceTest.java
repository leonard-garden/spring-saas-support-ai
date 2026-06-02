package com.leonardtrinh.supportsaas.billing;

import com.leonardtrinh.supportsaas.auth.PlanMisconfiguredException;
import com.leonardtrinh.supportsaas.common.ResourceNotFoundException;
import com.leonardtrinh.supportsaas.tenant.Business;
import com.leonardtrinh.supportsaas.tenant.BusinessRepository;
import com.stripe.model.Customer;
import com.stripe.model.checkout.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
}
