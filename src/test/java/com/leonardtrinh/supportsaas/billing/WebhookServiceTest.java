package com.leonardtrinh.supportsaas.billing;

import com.leonardtrinh.supportsaas.email.AsyncEmailSender;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

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

    @Mock
    private AsyncEmailSender asyncEmailSender;

    private WebhookServiceImpl webhookService;

    @BeforeEach
    void setUp() {
        webhookService = new WebhookServiceImpl(
                processedWebhookEventRepository,
                subscriptionRepository,
                subscriptionService,
                asyncEmailSender);
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
    // invoice.payment_succeeded
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("handle — invoice.payment_succeeded sets status ACTIVE and updates period dates")
    void handle_invoicePaymentSucceeded_setsActiveAndUpdatesPeriod() {
        String stripeSubId = "sub_pay_ok";
        long periodStart = 1_700_000_000L;
        long periodEnd = 1_702_592_000L;
        Event event = mockInvoiceEventWithPeriod("evt_010", "invoice.payment_succeeded", stripeSubId, periodStart, periodEnd);
        when(processedWebhookEventRepository.existsByStripeEventId("evt_010")).thenReturn(false);

        Subscription sub = new Subscription();
        sub.setStatus(SubscriptionStatus.PAST_DUE);
        when(subscriptionRepository.findByStripeSubscriptionId(stripeSubId)).thenReturn(Optional.of(sub));

        webhookService.handle(event);

        verify(subscriptionRepository).save(argThat(s ->
                s.getStatus() == SubscriptionStatus.ACTIVE
                && s.getCurrentPeriodStart() != null
                && s.getCurrentPeriodEnd() != null
                && s.getUpdatedAt() != null));
        verify(asyncEmailSender, never()).sendPaymentFailedAsync(any());
    }

    @Test
    @DisplayName("handle — invoice.payment_succeeded: subscription not found is silently ignored")
    void handle_invoicePaymentSucceeded_noLocalMatch_noSave() {
        String stripeSubId = "sub_unknown_pay";
        Event event = mockInvoiceEvent("evt_011", "invoice.payment_succeeded", stripeSubId);
        when(processedWebhookEventRepository.existsByStripeEventId("evt_011")).thenReturn(false);
        when(subscriptionRepository.findByStripeSubscriptionId(stripeSubId)).thenReturn(Optional.empty());

        webhookService.handle(event);

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("handle — invoice.payment_succeeded: deserialization failure does not throw")
    void handle_invoicePaymentSucceeded_deserializationFails_noException() {
        Event event = mockEventWithEmptyDeserializer("evt_012", "invoice.payment_succeeded");
        when(processedWebhookEventRepository.existsByStripeEventId("evt_012")).thenReturn(false);

        webhookService.handle(event);

        verify(subscriptionRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // invoice.payment_failed
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("handle — invoice.payment_failed sets status PAST_DUE and sends email to owner")
    void handle_invoicePaymentFailed_setsPastDueAndSendsEmail() {
        String stripeSubId = "sub_pay_fail";
        UUID businessId = UUID.randomUUID();
        String ownerEmail = "owner@example.com";
        Event event = mockInvoiceEvent("evt_020", "invoice.payment_failed", stripeSubId);
        when(processedWebhookEventRepository.existsByStripeEventId("evt_020")).thenReturn(false);

        Subscription sub = new Subscription();
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setBusinessId(businessId);
        when(subscriptionRepository.findByStripeSubscriptionId(stripeSubId)).thenReturn(Optional.of(sub));
        when(subscriptionRepository.findOwnerEmailByBusinessId(businessId)).thenReturn(Optional.of(ownerEmail));

        webhookService.handle(event);

        verify(subscriptionRepository).save(argThat(s ->
                s.getStatus() == SubscriptionStatus.PAST_DUE
                && s.getUpdatedAt() != null));
        verify(asyncEmailSender).sendPaymentFailedAsync(ownerEmail);
    }

    @Test
    @DisplayName("handle — invoice.payment_failed: owner email not found still saves PAST_DUE")
    void handle_invoicePaymentFailed_ownerEmailMissing_stillSavesPastDue() {
        String stripeSubId = "sub_pay_fail_no_email";
        UUID businessId = UUID.randomUUID();
        Event event = mockInvoiceEvent("evt_021", "invoice.payment_failed", stripeSubId);
        when(processedWebhookEventRepository.existsByStripeEventId("evt_021")).thenReturn(false);

        Subscription sub = new Subscription();
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setBusinessId(businessId);
        when(subscriptionRepository.findByStripeSubscriptionId(stripeSubId)).thenReturn(Optional.of(sub));
        when(subscriptionRepository.findOwnerEmailByBusinessId(businessId)).thenReturn(Optional.empty());

        webhookService.handle(event);

        verify(subscriptionRepository).save(argThat(s -> s.getStatus() == SubscriptionStatus.PAST_DUE));
        verify(asyncEmailSender, never()).sendPaymentFailedAsync(any());
    }

    @Test
    @DisplayName("handle — invoice.payment_failed: subscription not found is silently ignored")
    void handle_invoicePaymentFailed_noLocalMatch_noSave() {
        String stripeSubId = "sub_unknown_fail";
        Event event = mockInvoiceEvent("evt_022", "invoice.payment_failed", stripeSubId);
        when(processedWebhookEventRepository.existsByStripeEventId("evt_022")).thenReturn(false);
        when(subscriptionRepository.findByStripeSubscriptionId(stripeSubId)).thenReturn(Optional.empty());

        webhookService.handle(event);

        verify(subscriptionRepository, never()).save(any());
        verify(asyncEmailSender, never()).sendPaymentFailedAsync(any());
    }

    @Test
    @DisplayName("handle — invoice.payment_failed: deserialization failure does not throw")
    void handle_invoicePaymentFailed_deserializationFails_noException() {
        Event event = mockEventWithEmptyDeserializer("evt_023", "invoice.payment_failed");
        when(processedWebhookEventRepository.existsByStripeEventId("evt_023")).thenReturn(false);

        webhookService.handle(event);

        verify(subscriptionRepository, never()).save(any());
        verify(asyncEmailSender, never()).sendPaymentFailedAsync(any());
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

    private Event mockInvoiceEvent(String id, String type, String stripeSubId) {
        Invoice.Parent.SubscriptionDetails subDetails = mock(Invoice.Parent.SubscriptionDetails.class);
        when(subDetails.getSubscription()).thenReturn(stripeSubId);

        Invoice.Parent parent = mock(Invoice.Parent.class);
        when(parent.getSubscriptionDetails()).thenReturn(subDetails);

        Invoice invoice = mock(Invoice.class);
        when(invoice.getParent()).thenReturn(parent);

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(invoice));

        Event event = mock(Event.class);
        when(event.getId()).thenReturn(id);
        when(event.getType()).thenReturn(type);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        return event;
    }

    private Event mockInvoiceEventWithPeriod(
            String id, String type, String stripeSubId, long periodStart, long periodEnd) {
        Invoice.Parent.SubscriptionDetails subDetails = mock(Invoice.Parent.SubscriptionDetails.class);
        when(subDetails.getSubscription()).thenReturn(stripeSubId);

        Invoice.Parent parent = mock(Invoice.Parent.class);
        when(parent.getSubscriptionDetails()).thenReturn(subDetails);

        Invoice invoice = mock(Invoice.class);
        when(invoice.getParent()).thenReturn(parent);
        when(invoice.getPeriodStart()).thenReturn(periodStart);
        when(invoice.getPeriodEnd()).thenReturn(periodEnd);

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(invoice));

        Event event = mock(Event.class);
        when(event.getId()).thenReturn(id);
        when(event.getType()).thenReturn(type);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        return event;
    }

    private Event mockEventWithEmptyDeserializer(String id, String type) {
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.empty());

        Event event = mock(Event.class);
        when(event.getId()).thenReturn(id);
        when(event.getType()).thenReturn(type);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
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
