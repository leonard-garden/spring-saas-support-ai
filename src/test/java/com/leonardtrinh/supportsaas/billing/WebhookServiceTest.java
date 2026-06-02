package com.leonardtrinh.supportsaas.billing;

import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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

    private WebhookServiceImpl webhookService;

    @BeforeEach
    void setUp() {
        webhookService = new WebhookServiceImpl(
                processedWebhookEventRepository,
                subscriptionRepository,
                subscriptionService);
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
        Event event = mockEvent("evt_002", "checkout.session.completed");
        when(processedWebhookEventRepository.existsByStripeEventId("evt_002")).thenReturn(false);

        webhookService.handle(event);

        verify(processedWebhookEventRepository).save(argThat(pwe ->
                "evt_002".equals(pwe.getStripeEventId())
                && "checkout.session.completed".equals(pwe.getEventType())));
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
}
