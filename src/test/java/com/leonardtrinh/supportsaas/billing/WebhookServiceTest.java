package com.leonardtrinh.supportsaas.billing;

import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.SubscriptionItemCollection;
import com.stripe.model.checkout.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock
    private ProcessedWebhookEventRepository processedWebhookEventRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private StripeService stripeService;

    private WebhookServiceImpl webhookService;

    @BeforeEach
    void setUp() {
        webhookService = new WebhookServiceImpl(
                processedWebhookEventRepository,
                subscriptionRepository,
                subscriptionService,
                planRepository,
                stripeService);
    }

    // -----------------------------------------------------------------------
    // Idempotency gate
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("handle — skips processing when event already recorded")
    void handle_alreadyProcessed_skipsProcessing() {
        Event event = mockEvent("evt_001", "customer.subscription.updated");
        when(processedWebhookEventRepository.existsByStripeEventId("evt_001")).thenReturn(true);

        webhookService.handle(event);

        verify(processedWebhookEventRepository, never()).save(any());
        verify(subscriptionRepository, never()).findByStripeSubscriptionId(any());
    }

    @Test
    @DisplayName("handle — records event before delegating on first delivery")
    void handle_newEvent_savesProcessedRecord() {
        Event event = mockEvent("evt_002", "payment_intent.succeeded");
        when(processedWebhookEventRepository.existsByStripeEventId("evt_002")).thenReturn(false);

        webhookService.handle(event);

        verify(processedWebhookEventRepository).save(argThat(pwe ->
                "evt_002".equals(pwe.getStripeEventId())
                && "payment_intent.succeeded".equals(pwe.getEventType())));
    }

    // -----------------------------------------------------------------------
    // Subscription event routing
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("handle — syncs subscription for customer.subscription.updated when found by stripe ID")
    void handle_subscriptionUpdated_syncsMatchingSubscription() {
        String stripeSubId = "sub_abc123";
        Event event = mockSubscriptionEvent("evt_003", "customer.subscription.updated", stripeSubId);
        when(processedWebhookEventRepository.existsByStripeEventId("evt_003")).thenReturn(false);

        Subscription localSub = new Subscription();
        when(subscriptionRepository.findByStripeSubscriptionId(stripeSubId))
                .thenReturn(Optional.of(localSub));

        webhookService.handle(event);

        verify(subscriptionService).syncFromStripe(localSub);
    }

    @Test
    @DisplayName("handle — skips sync when local subscription not found for stripe ID")
    void handle_subscriptionUpdated_noLocalMatch_noSync() {
        String stripeSubId = "sub_unknown";
        Event event = mockSubscriptionEvent("evt_004", "customer.subscription.deleted", stripeSubId);
        when(processedWebhookEventRepository.existsByStripeEventId("evt_004")).thenReturn(false);

        when(subscriptionRepository.findByStripeSubscriptionId(stripeSubId))
                .thenReturn(Optional.empty());

        webhookService.handle(event);

        verify(subscriptionService, never()).syncFromStripe(any());
    }

    @Test
    @DisplayName("handle — deserialization failure logs warning without crashing")
    void handle_subscriptionEvent_deserializationFails_noException() {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn("evt_005");
        when(event.getType()).thenReturn("customer.subscription.created");
        when(processedWebhookEventRepository.existsByStripeEventId("evt_005")).thenReturn(false);

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.empty());
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);

        // Should not throw
        webhookService.handle(event);

        verify(subscriptionService, never()).syncFromStripe(any());
    }

    @Test
    @DisplayName("handle — unhandled event type is ignored without error")
    void handle_unknownEventType_noProcessing() {
        Event event = mockEvent("evt_006", "payment_intent.created");
        when(processedWebhookEventRepository.existsByStripeEventId("evt_006")).thenReturn(false);

        webhookService.handle(event);

        verify(subscriptionRepository, never()).findByStripeSubscriptionId(any());
        verify(subscriptionService, never()).syncFromStripe(any());
    }

    // -----------------------------------------------------------------------
    // checkout.session.completed
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("handle — checkout.session.completed activates subscription with period dates and plan")
    void handle_checkoutCompleted_activatesSubscription() {
        String stripeSubId = "sub_checkout_001";
        String stripeCustomerId = "cus_checkout_001";
        UUID planId = UUID.randomUUID();
        UUID newPlanId = UUID.randomUUID();
        long periodStart = 1_700_000_000L;
        long periodEnd   = 1_702_592_000L;

        Event event = mockCheckoutSessionEvent("evt_checkout_001", stripeSubId, stripeCustomerId);
        when(processedWebhookEventRepository.existsByStripeEventId("evt_checkout_001")).thenReturn(false);

        Subscription localSub = new Subscription();
        localSub.setPlanId(planId);
        when(subscriptionRepository.findByStripeCustomerId(stripeCustomerId))
                .thenReturn(Optional.of(localSub));

        com.stripe.model.Subscription stripeSub = mockStripeSubscription(stripeSubId, "price_pro", periodStart, periodEnd);
        when(stripeService.retrieveSubscription(stripeSubId)).thenReturn(stripeSub);

        Plan plan = new Plan();
        ReflectionTestUtils.setField(plan, "id", newPlanId);
        when(planRepository.findByStripePriceId("price_pro")).thenReturn(Optional.of(plan));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        webhookService.handle(event);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        Subscription saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(saved.getStripeSubscriptionId()).isEqualTo(stripeSubId);
        assertThat(saved.getStripeCustomerId()).isEqualTo(stripeCustomerId);
        assertThat(saved.getPlanId()).isEqualTo(newPlanId);
        assertThat(saved.getCurrentPeriodStart().getEpochSecond()).isEqualTo(periodStart);
        assertThat(saved.getCurrentPeriodEnd().getEpochSecond()).isEqualTo(periodEnd);
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("handle — checkout.session.completed keeps existing planId when Stripe price not matched")
    void handle_checkoutCompleted_unknownPriceId_keepsExistingPlanId() {
        String stripeSubId = "sub_checkout_002";
        String stripeCustomerId = "cus_checkout_002";
        UUID existingPlanId = UUID.randomUUID();
        long periodStart = 1_700_000_000L;
        long periodEnd   = 1_702_592_000L;

        Event event = mockCheckoutSessionEvent("evt_checkout_002", stripeSubId, stripeCustomerId);
        when(processedWebhookEventRepository.existsByStripeEventId("evt_checkout_002")).thenReturn(false);

        Subscription localSub = new Subscription();
        localSub.setPlanId(existingPlanId);
        when(subscriptionRepository.findByStripeCustomerId(stripeCustomerId))
                .thenReturn(Optional.of(localSub));

        com.stripe.model.Subscription stripeSub = mockStripeSubscription(stripeSubId, "price_unknown", periodStart, periodEnd);
        when(stripeService.retrieveSubscription(stripeSubId)).thenReturn(stripeSub);
        when(planRepository.findByStripePriceId("price_unknown")).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        webhookService.handle(event);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getPlanId()).isEqualTo(existingPlanId);
        assertThat(captor.getValue().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("handle — checkout.session.completed skips when local subscription not found by customer ID")
    void handle_checkoutCompleted_noLocalSubscription_skips() {
        String stripeSubId = "sub_checkout_003";
        String stripeCustomerId = "cus_checkout_003";

        Event event = mockCheckoutSessionEvent("evt_checkout_003", stripeSubId, stripeCustomerId);
        when(processedWebhookEventRepository.existsByStripeEventId("evt_checkout_003")).thenReturn(false);
        when(subscriptionRepository.findByStripeCustomerId(stripeCustomerId)).thenReturn(Optional.empty());

        webhookService.handle(event);

        verify(subscriptionRepository, never()).save(any());
        verify(stripeService, never()).retrieveSubscription(any());
    }

    @Test
    @DisplayName("handle — checkout.session.completed skips when deserialization fails")
    void handle_checkoutCompleted_deserializationFails_skips() {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn("evt_checkout_004");
        when(event.getType()).thenReturn("checkout.session.completed");
        when(processedWebhookEventRepository.existsByStripeEventId("evt_checkout_004")).thenReturn(false);

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.empty());
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);

        webhookService.handle(event);

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("handle — checkout.session.completed skips when stripeSubId is null")
    void handle_checkoutCompleted_nullStripeSubId_skips() {
        // Session with null subscription ID (e.g. setup mode, no subscription created)
        Event event = mockCheckoutSessionEvent("evt_checkout_005", null, "cus_checkout_005");
        when(processedWebhookEventRepository.existsByStripeEventId("evt_checkout_005")).thenReturn(false);

        webhookService.handle(event);

        verify(subscriptionRepository, never()).save(any());
        verify(subscriptionRepository, never()).findByStripeCustomerId(any());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Event mockEvent(String id, String type) {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(id);
        when(event.getType()).thenReturn(type);
        return event;
    }

    private Event mockSubscriptionEvent(String id, String type, String stripeSubId) {
        com.stripe.model.Subscription stripeSub = mock(com.stripe.model.Subscription.class);
        when(stripeSub.getId()).thenReturn(stripeSubId);

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(stripeSub));

        Event event = mock(Event.class);
        when(event.getId()).thenReturn(id);
        when(event.getType()).thenReturn(type);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        return event;
    }

    private Event mockCheckoutSessionEvent(String id, String stripeSubId, String stripeCustomerId) {
        Session session = mock(Session.class);
        when(session.getSubscription()).thenReturn(stripeSubId);
        when(session.getCustomer()).thenReturn(stripeCustomerId);

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(session));

        Event event = mock(Event.class);
        when(event.getId()).thenReturn(id);
        when(event.getType()).thenReturn("checkout.session.completed");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        return event;
    }

    private com.stripe.model.Subscription mockStripeSubscription(
            String subId, String priceId, long periodStart, long periodEnd) {
        com.stripe.model.Price price = mock(com.stripe.model.Price.class);
        lenient().when(price.getId()).thenReturn(priceId);

        SubscriptionItem item = mock(SubscriptionItem.class);
        lenient().when(item.getPrice()).thenReturn(price);
        lenient().when(item.getCurrentPeriodStart()).thenReturn(periodStart);
        lenient().when(item.getCurrentPeriodEnd()).thenReturn(periodEnd);

        SubscriptionItemCollection items = mock(SubscriptionItemCollection.class);
        lenient().when(items.getData()).thenReturn(List.of(item));

        com.stripe.model.Subscription stripeSub = mock(com.stripe.model.Subscription.class);
        lenient().when(stripeSub.getId()).thenReturn(subId);
        lenient().when(stripeSub.getItems()).thenReturn(items);
        return stripeSub;
    }
}
