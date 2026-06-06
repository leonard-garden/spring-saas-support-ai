package com.leonardtrinh.supportsaas.billing;

import com.leonardtrinh.supportsaas.email.AsyncEmailSender;
import com.leonardtrinh.supportsaas.tenant.Business;
import com.leonardtrinh.supportsaas.tenant.BusinessRepository;
import com.stripe.model.Customer;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BillingServiceTest covers the two core billing orchestration scenarios:
 *
 * <ol>
 *   <li>Checkout session creation — delegated to {@link SubscriptionServiceImpl#startCheckout}</li>
 *   <li>Subscription activation — delegated to {@link WebhookServiceImpl#handleCheckoutSessionCompleted},
 *       which is the code path that sets {@code status=ACTIVE}, {@code stripeSubscriptionId},
 *       and {@code stripeCustomerId} after a successful Stripe Checkout.</li>
 * </ol>
 *
 * <p>No Spring context — pure Mockito, per project testing rules.
 */
@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    // =========================================================================
    // Shared mocks
    // =========================================================================

    @Mock SubscriptionRepository subscriptionRepository;
    @Mock PlanRepository planRepository;
    @Mock BusinessRepository businessRepository;
    @Mock StripeService stripeService;
    @Mock ProcessedWebhookEventRepository processedWebhookEventRepository;
    @Mock AsyncEmailSender asyncEmailSender;
    @Mock SubscriptionService subscriptionService;

    // System-under-test instances (wired in @BeforeEach or per-nested setUp)
    SubscriptionServiceImpl checkoutService;
    WebhookServiceImpl webhookService;

    @BeforeEach
    void setUp() {
        checkoutService = new SubscriptionServiceImpl(
                subscriptionRepository, planRepository, businessRepository,
                stripeService, "http://localhost:8081", "http://localhost:3000");

        webhookService = new WebhookServiceImpl(
                processedWebhookEventRepository,
                subscriptionRepository,
                subscriptionService,
                asyncEmailSender,
                planRepository,
                stripeService);
    }

    // =========================================================================
    // 1. createCheckoutSession — happy path
    // =========================================================================

    @Nested
    @DisplayName("createCheckoutSession — happy path")
    class CreateCheckoutSessionHappyPath {

        @Test
        @DisplayName("returns session URL when tenant is TRIALING and plan exists")
        void startCheckout_trialingTenant_returnsCheckoutUrl() {
            UUID businessId = UUID.randomUUID();
            UUID planId = UUID.randomUUID();

            Plan pro = planWith(planId, "pro", "price_pro_monthly");
            when(planRepository.findBySlug("pro")).thenReturn(Optional.of(pro));

            Subscription trialing = new Subscription();
            trialing.setStatus(SubscriptionStatus.TRIALING);
            when(subscriptionRepository.findActiveByBusinessId(businessId))
                    .thenReturn(Optional.of(trialing));

            Business business = new Business();
            when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
            when(businessRepository.save(any(Business.class))).thenReturn(business);

            Customer customer = new Customer();
            ReflectionTestUtils.setField(customer, "id", "cus_test123");
            when(stripeService.getOrCreateCustomer(anyString(), any(UUID.class)))
                    .thenReturn(customer);

            Session session = new Session();
            ReflectionTestUtils.setField(session, "url", "https://checkout.stripe.com/pay/cs_test_happy");
            when(stripeService.createCheckoutSession(
                    anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(session);

            CheckoutResponse response = checkoutService.startCheckout(businessId, "admin@example.com", "pro");

            assertThat(response.url())
                    .isEqualTo("https://checkout.stripe.com/pay/cs_test_happy");
            verify(stripeService).getOrCreateCustomer("admin@example.com", businessId);
            verify(stripeService).createCheckoutSession(
                    anyString(), anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("returns session URL when tenant has no existing subscription")
        void startCheckout_noExistingSubscription_returnsCheckoutUrl() {
            UUID businessId = UUID.randomUUID();
            UUID planId = UUID.randomUUID();

            Plan starter = planWith(planId, "starter", "price_starter_monthly");
            when(planRepository.findBySlug("starter")).thenReturn(Optional.of(starter));
            when(subscriptionRepository.findActiveByBusinessId(businessId))
                    .thenReturn(Optional.empty());

            Business business = new Business();
            when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
            when(businessRepository.save(any(Business.class))).thenReturn(business);

            Customer customer = new Customer();
            ReflectionTestUtils.setField(customer, "id", "cus_new456");
            when(stripeService.getOrCreateCustomer(anyString(), any(UUID.class)))
                    .thenReturn(customer);

            Session session = new Session();
            ReflectionTestUtils.setField(session, "url", "https://checkout.stripe.com/pay/cs_new");
            when(stripeService.createCheckoutSession(
                    anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(session);

            CheckoutResponse response = checkoutService.startCheckout(businessId, "owner@example.com", "starter");

            assertThat(response.url())
                    .isEqualTo("https://checkout.stripe.com/pay/cs_new");
        }
    }

    // =========================================================================
    // 2. createCheckoutSession — already-active subscription → exception
    // =========================================================================

    @Nested
    @DisplayName("createCheckoutSession — already ACTIVE subscription throws")
    class CreateCheckoutSessionAlreadyActive {

        @Test
        @DisplayName("throws AlreadySubscribedException when subscription is ACTIVE")
        void startCheckout_activeSubscription_throwsAlreadySubscribed() {
            UUID businessId = UUID.randomUUID();
            UUID planId = UUID.randomUUID();

            Plan pro = planWith(planId, "pro", "price_pro_monthly");
            when(planRepository.findBySlug("pro")).thenReturn(Optional.of(pro));

            Subscription active = new Subscription();
            active.setStatus(SubscriptionStatus.ACTIVE);
            when(subscriptionRepository.findActiveByBusinessId(businessId))
                    .thenReturn(Optional.of(active));

            assertThatThrownBy(() ->
                    checkoutService.startCheckout(businessId, "admin@example.com", "pro"))
                    .isInstanceOf(AlreadySubscribedException.class);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when plan slug is 'free'")
        void startCheckout_freePlanSlug_throwsResourceNotFound() {
            UUID businessId = UUID.randomUUID();

            assertThatThrownBy(() ->
                    checkoutService.startCheckout(businessId, "admin@example.com", "free"))
                    .isInstanceOf(com.leonardtrinh.supportsaas.common.ResourceNotFoundException.class);
        }
    }

    // =========================================================================
    // 3. activate — correct DB fields set after checkout.session.completed
    // =========================================================================

    @Nested
    @DisplayName("activate — checkout.session.completed sets correct DB fields")
    class Activate {

        @Test
        @DisplayName("sets status=ACTIVE, stripeSubscriptionId, stripeCustomerId on the subscription")
        void handleCheckoutCompleted_setsActivationFields() {
            String stripeSubId = "sub_activated123";
            String stripeCustomerId = "cus_activated456";

            // Subscription found by customer ID (pre-existing TRIALING sub)
            Subscription sub = new Subscription();
            sub.setStatus(SubscriptionStatus.TRIALING);
            when(subscriptionRepository.findByStripeCustomerId(stripeCustomerId))
                    .thenReturn(Optional.of(sub));
            when(subscriptionRepository.save(any(Subscription.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Stripe sub has no items (simplest activate path — plan not re-resolved)
            com.stripe.model.Subscription stripeSub = new com.stripe.model.Subscription();
            ReflectionTestUtils.setField(stripeSub, "items", null);
            when(stripeService.retrieveSubscription(stripeSubId)).thenReturn(stripeSub);

            // Idempotency gate — event not yet processed
            when(processedWebhookEventRepository.existsByStripeEventId(anyString()))
                    .thenReturn(false);
            when(processedWebhookEventRepository.save(any()))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Build a checkout.session.completed event with the Session payload
            com.stripe.model.Event event = buildCheckoutEvent("evt_activate_001", stripeSubId, stripeCustomerId);

            webhookService.handle(event);

            // Capture what was saved and verify all activation fields
            ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
            verify(subscriptionRepository).save(captor.capture());
            Subscription saved = captor.getValue();

            assertThat(saved.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(saved.getStripeSubscriptionId()).isEqualTo(stripeSubId);
            assertThat(saved.getStripeCustomerId()).isEqualTo(stripeCustomerId);
        }

        @Test
        @DisplayName("does not save when stripeSubscriptionId is missing from session")
        void handleCheckoutCompleted_missingStripeSubId_doesNotSave() {
            when(processedWebhookEventRepository.existsByStripeEventId(anyString()))
                    .thenReturn(false);
            when(processedWebhookEventRepository.save(any()))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Session with null subscription ID
            com.stripe.model.Event event = buildCheckoutEvent("evt_missing_sub", null, "cus_abc");

            webhookService.handle(event);

            // subscriptionRepository.save must NOT be called
            verify(subscriptionRepository, org.mockito.Mockito.never()).save(any());
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Plan planWith(UUID id, String slug, String stripePriceId) {
        Plan plan = new Plan();
        ReflectionTestUtils.setField(plan, "id", id);
        ReflectionTestUtils.setField(plan, "name", slug);
        ReflectionTestUtils.setField(plan, "slug", slug);
        ReflectionTestUtils.setField(plan, "stripePriceId", stripePriceId);
        ReflectionTestUtils.setField(plan, "priceUsdMonthly", BigDecimal.valueOf(29));
        ReflectionTestUtils.setField(plan, "maxKnowledgeBases", 3);
        ReflectionTestUtils.setField(plan, "maxDocumentsPerKb", 50);
        ReflectionTestUtils.setField(plan, "maxMessagesPerMonth", 1000);
        ReflectionTestUtils.setField(plan, "maxMembers", 3);
        ReflectionTestUtils.setField(plan, "isActive", true);
        return plan;
    }

    /**
     * Builds a {@code checkout.session.completed} Stripe event with the given
     * subscription and customer IDs. The Session is mocked so that
     * {@code getSubscription()} and {@code getCustomer()} return the plain
     * String IDs — avoids fighting Stripe SDK's internal {@code ExpandableField} type.
     */
    private com.stripe.model.Event buildCheckoutEvent(
            String eventId, String stripeSubId, String stripeCustomerId) {

        Session session = mock(Session.class);
        when(session.getSubscription()).thenReturn(stripeSubId);
        when(session.getCustomer()).thenReturn(stripeCustomerId);

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(session));

        com.stripe.model.Event event = mock(com.stripe.model.Event.class);
        when(event.getId()).thenReturn(eventId);
        when(event.getType()).thenReturn("checkout.session.completed");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);

        return event;
    }
}
