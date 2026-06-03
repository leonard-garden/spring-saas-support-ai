package com.leonardtrinh.supportsaas.billing;

import com.leonardtrinh.supportsaas.auth.AuthResponse;
import com.leonardtrinh.supportsaas.common.ApiResponse;
import com.leonardtrinh.supportsaas.common.BaseIT;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the billing flow: signup → TRIALING subscription,
 * checkout session creation, webhook activation, and quota enforcement.
 *
 * <p>StripeService is mocked via @MockBean — no real Stripe calls are made.
 */
class BillingIT extends BaseIT {

    @MockBean
    private StripeService stripeService;

    @Autowired
    private WebhookService webhookService;

    @Autowired
    private QuotaService quotaService;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        configureRestTemplate();
        // Default stub: getOrCreateCustomer returns a mock Customer
        Customer mockCustomer = mock(Customer.class);
        when(mockCustomer.getId()).thenReturn("cus_test_" + UUID.randomUUID().toString().substring(0, 8));
        when(stripeService.getOrCreateCustomer(anyString(), any(UUID.class))).thenReturn(mockCustomer);

        // Default stub: createCheckoutSession returns a mock Session with a URL
        Session mockSession = mock(Session.class);
        when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/pay/test_session");
        // nullable(String.class) for priceId — seeded plans have NULL stripe_price_id in test DB
        when(stripeService.createCheckoutSession(anyString(), nullable(String.class), anyString(), anyString(), anyString()))
                .thenReturn(mockSession);
    }

    // -------------------------------------------------------------------------
    // Test 1: Signup → subscription created with TRIALING status on Pro plan
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("signup: creates subscription with status=TRIALING on pro plan")
    void signup_createsTrialingProSubscription() {
        AuthResponse auth = doSignup(uniqueName("BillingCo"), uniqueEmail("billing"));

        UUID businessId = auth.businessId();
        assertThat(businessId).isNotNull();

        Optional<Subscription> subOpt = subscriptionRepository.findActiveByBusinessId(businessId);
        assertThat(subOpt).isPresent();

        Subscription sub = subOpt.get();
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.TRIALING);

        // Verify it is linked to the 'pro' plan
        Plan plan = planRepository.findById(sub.getPlanId()).orElseThrow();
        assertThat(plan.getSlug()).isEqualTo("pro");
    }

    // -------------------------------------------------------------------------
    // Test 2: POST /billing/checkout → returns session URL (Stripe mocked)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /billing/checkout: returns checkout session URL for starter plan")
    void checkout_returnsSessionUrl() {
        // Signup creates OWNER role; SecurityConfig grants OWNER access to /billing/checkout
        AuthResponse auth = doSignup(uniqueName("CheckoutCo"), uniqueEmail("checkout"));

        ResponseEntity<ApiResponse<CheckoutResponse>> resp = restTemplate.exchange(
                "/api/v1/billing/checkout",
                HttpMethod.POST,
                new HttpEntity<>(new CheckoutRequest("starter"), authHeader(auth.accessToken())),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().success()).isTrue();
        assertThat(resp.getBody().data().checkoutUrl()).isEqualTo("https://checkout.stripe.com/pay/test_session");
    }

    // -------------------------------------------------------------------------
    // Test 3: checkout.session.completed webhook → subscription status=ACTIVE
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("checkout.session.completed webhook: activates subscription to ACTIVE")
    void webhookCheckoutCompleted_activatesSubscription() {
        AuthResponse auth = doSignup(uniqueName("WebhookCo"), uniqueEmail("webhook"));
        UUID businessId = auth.businessId();

        // Seed a stripe_customer_id on the subscription so the webhook handler can look it up
        String fakeCustomerId = "cus_wh_" + UUID.randomUUID().toString().substring(0, 8);
        String fakeSubId = "sub_wh_" + UUID.randomUUID().toString().substring(0, 8);

        jdbcTemplate.update(
                "UPDATE subscriptions SET stripe_customer_id = ? WHERE business_id = ?",
                fakeCustomerId, businessId);

        // Mock retrieveSubscription to return a stripe Subscription with items
        com.stripe.model.Subscription stripeSubscription = buildMockStripeSubscription(fakeSubId, "active");
        when(stripeService.retrieveSubscription(fakeSubId)).thenReturn(stripeSubscription);

        // Build a fake checkout.session.completed Event
        Event event = buildCheckoutCompletedEvent(fakeCustomerId, fakeSubId);

        webhookService.handle(event);

        Subscription sub = subscriptionRepository.findActiveByBusinessId(businessId).orElseThrow();
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(sub.getStripeSubscriptionId()).isEqualTo(fakeSubId);
    }

    // -------------------------------------------------------------------------
    // Test 4a: Quota — tenant at limit → QuotaExceededException thrown
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("quota: free plan tenant at KB limit throws QuotaExceededException")
    void quota_freePlanAtLimit_throwsQuotaExceededException() {
        AuthResponse auth = doSignup(uniqueName("QuotaCo"), uniqueEmail("quota"));
        UUID businessId = auth.businessId();

        // Force the subscription to a free plan so we can test hard limits
        Plan freePlan = planRepository.findBySlug("free").orElseThrow();
        jdbcTemplate.update(
                "UPDATE subscriptions SET plan_id = ?, status = 'ACTIVE' WHERE business_id = ?",
                freePlan.getId(), businessId);

        int freePlanKbLimit = freePlan.getMaxKnowledgeBases(); // e.g. 1

        // currentCount == limit should trigger the exception
        assertThatThrownBy(() -> quotaService.checkKnowledgeBaseQuota(businessId, freePlanKbLimit))
                .isInstanceOf(QuotaExceededException.class)
                .hasMessageContaining("knowledge_bases");
    }

    // -------------------------------------------------------------------------
    // Test 4b: Quota — business plan tenant → unlimited (no exception)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("quota: business plan tenant is unlimited — no exception thrown")
    void quota_businessPlan_isUnlimited() {
        AuthResponse auth = doSignup(uniqueName("BusinessPlanCo"), uniqueEmail("bizplan"));
        UUID businessId = auth.businessId();

        Plan businessPlan = planRepository.findBySlug("business").orElseThrow();
        jdbcTemplate.update(
                "UPDATE subscriptions SET plan_id = ?, status = 'ACTIVE' WHERE business_id = ?",
                businessPlan.getId(), businessId);

        // Even with an absurdly large count, no exception should be thrown
        quotaService.checkKnowledgeBaseQuota(businessId, 1_000_000L);
        quotaService.checkDocumentQuota(businessId, 1_000_000L);
        quotaService.checkMessageQuota(businessId, 1_000_000L);
        // Test passes if no exception is thrown
    }

    // -------------------------------------------------------------------------
    // Test 4c: Quota — trialing paid tenant gets 10% grace period
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("quota: trialing tenant gets 10% grace — below grace limit passes, at limit throws")
    void quota_trialingTenant_gracePeriodApplied() {
        AuthResponse auth = doSignup(uniqueName("GraceCo"), uniqueEmail("grace"));
        UUID businessId = auth.businessId();

        // Signup creates a TRIALING pro subscription — use it as-is
        Plan proPlan = planRepository.findBySlug("pro").orElseThrow();
        int hardLimit = proPlan.getMaxKnowledgeBases(); // e.g. 10
        long graceLimit = (long) Math.floor(hardLimit * 1.1); // e.g. 11

        // At hard limit: should NOT throw (grace period covers it)
        quotaService.checkKnowledgeBaseQuota(businessId, hardLimit);

        // One below grace limit: should NOT throw
        quotaService.checkKnowledgeBaseQuota(businessId, graceLimit - 1);

        // AT grace limit: should throw
        assertThatThrownBy(() -> quotaService.checkKnowledgeBaseQuota(businessId, graceLimit))
                .isInstanceOf(QuotaExceededException.class);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a minimal fake Stripe checkout.session.completed Event.
     * We use Mockito to stub the deserialization path that WebhookServiceImpl relies on.
     */
    private Event buildCheckoutCompletedEvent(String customerId, String subscriptionId) {
        Session session = mock(Session.class);
        when(session.getCustomer()).thenReturn(customerId);
        when(session.getSubscription()).thenReturn(subscriptionId);

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(session));

        Event event = mock(Event.class);
        when(event.getId()).thenReturn("evt_" + UUID.randomUUID().toString().replace("-", ""));
        when(event.getType()).thenReturn("checkout.session.completed");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);

        return event;
    }

    /**
     * Builds a minimal mock Stripe Subscription for use in retrieveSubscription stubs.
     * Returns a subscription with empty items so the webhook handler can safely navigate it
     * without NPE.
     */
    private com.stripe.model.Subscription buildMockStripeSubscription(String subId, String status) {
        com.stripe.model.SubscriptionItemCollection items =
                mock(com.stripe.model.SubscriptionItemCollection.class);
        when(items.getData()).thenReturn(java.util.List.of());

        com.stripe.model.Subscription sub = mock(com.stripe.model.Subscription.class);
        when(sub.getId()).thenReturn(subId);
        when(sub.getStatus()).thenReturn(status);
        when(sub.getItems()).thenReturn(items);

        return sub;
    }
}
