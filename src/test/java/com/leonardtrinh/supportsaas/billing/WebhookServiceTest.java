package com.leonardtrinh.supportsaas.billing;

import com.leonardtrinh.supportsaas.email.AsyncEmailSender;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.Price;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private AsyncEmailSender asyncEmailSender;

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
                asyncEmailSender,
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
    @DisplayName("handle — saves updated subscription for customer.subscription.updated when found by stripe ID")
    void handle_subscriptionUpdated_syncsMatchingSubscription() {
        String stripeSubId = "sub_abc123";
        String priceId = "price_pro";
        UUID planId = UUID.randomUUID();

        Subscription localSub = buildSubscription(stripeSubId, planId, null);
        Event event = mockSubscriptionUpdatedEvent("evt_003", stripeSubId, "active",
                false, priceId, 100L, 200L);
        when(processedWebhookEventRepository.existsByStripeEventId("evt_003")).thenReturn(false);
        when(subscriptionRepository.findByStripeSubscriptionId(stripeSubId))
                .thenReturn(Optional.of(localSub));

        Plan plan = new Plan();
        when(planRepository.findByStripePriceId(priceId)).thenReturn(Optional.of(plan));

        webhookService.handle(event);

        verify(subscriptionRepository).save(localSub);
        verify(subscriptionService, never()).syncFromStripe(any());
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
    // customer.subscription.deleted — downgrade to Free
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("handle — subscription.deleted downgrades to Free plan with status ACTIVE")
    void handle_subscriptionDeleted_downgradesToFreePlan() {
        String stripeSubId = "sub_deleted_001";
        Event event = mockSubscriptionEvent("evt_013", "customer.subscription.deleted", stripeSubId);
        when(processedWebhookEventRepository.existsByStripeEventId("evt_013")).thenReturn(false);

        Subscription localSub = new Subscription();
        localSub.setStatus(SubscriptionStatus.PAST_DUE);
        localSub.setCancelAtPeriodEnd(true);
        localSub.setStripeSubscriptionId(stripeSubId);
        when(subscriptionRepository.findByStripeSubscriptionId(stripeSubId))
                .thenReturn(Optional.of(localSub));

        UUID freePlanId = UUID.randomUUID();
        Plan freePlan = mock(Plan.class);
        when(freePlan.getId()).thenReturn(freePlanId);
        when(planRepository.findBySlug("free")).thenReturn(Optional.of(freePlan));

        webhookService.handle(event);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        Subscription saved = captor.getValue();
        assertThat(saved.getPlanId()).isEqualTo(freePlanId);
        assertThat(saved.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(saved.isCancelAtPeriodEnd()).isFalse();
        assertThat(saved.getStripeSubscriptionId()).isNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("handle — subscription.deleted skips downgrade when local subscription not found")
    void handle_subscriptionDeleted_noLocalMatch_noSave() {
        String stripeSubId = "sub_deleted_unknown";
        Event event = mockSubscriptionEvent("evt_014", "customer.subscription.deleted", stripeSubId);
        when(processedWebhookEventRepository.existsByStripeEventId("evt_014")).thenReturn(false);

        when(subscriptionRepository.findByStripeSubscriptionId(stripeSubId))
                .thenReturn(Optional.empty());

        webhookService.handle(event);

        verify(subscriptionRepository, never()).save(any());
        verify(planRepository, never()).findBySlug(any());
    }

    @Test
    @DisplayName("handle — subscription.deleted throws when Free plan missing from database")
    void handle_subscriptionDeleted_freePlanMissing_throwsIllegalState() {
        String stripeSubId = "sub_deleted_002";
        Event event = mockSubscriptionEvent("evt_015", "customer.subscription.deleted", stripeSubId);
        when(processedWebhookEventRepository.existsByStripeEventId("evt_015")).thenReturn(false);

        Subscription localSub = new Subscription();
        localSub.setStatus(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByStripeSubscriptionId(stripeSubId))
                .thenReturn(Optional.of(localSub));

        when(planRepository.findBySlug("free")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> webhookService.handle(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Free plan not found");
    }

    @Test
    @DisplayName("handle — subscription.deleted with deserialization failure logs warning without crash")
    void handle_subscriptionDeleted_deserializationFails_noException() {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn("evt_016");
        when(event.getType()).thenReturn("customer.subscription.deleted");
        when(processedWebhookEventRepository.existsByStripeEventId("evt_016")).thenReturn(false);

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.empty());
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);

        // Should not throw
        webhookService.handle(event);

        verify(subscriptionRepository, never()).save(any());
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
        Event event = mockCheckoutSessionEvent("evt_checkout_005", null, "cus_checkout_005");
        when(processedWebhookEventRepository.existsByStripeEventId("evt_checkout_005")).thenReturn(false);

        webhookService.handle(event);

        verify(subscriptionRepository, never()).save(any());
        verify(subscriptionRepository, never()).findByStripeCustomerId(any());
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
    // handleSubscriptionUpdated — upgrade / renewal path
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("subscriptionUpdated — upgrades planId from Stripe price ID when no pendingPlanId")
    void handleSubscriptionUpdated_upgradePath_updatesPlanId() {
        String stripeSubId = "sub_upgrade";
        String priceId = "price_pro";
        UUID newPlanId = UUID.randomUUID();

        Subscription localSub = buildSubscription(stripeSubId, null, null);
        Event event = mockSubscriptionUpdatedEvent("evt_upg1", stripeSubId, "active",
                false, priceId, epochSecond(100L), epochSecond(200L));

        when(processedWebhookEventRepository.existsByStripeEventId("evt_upg1")).thenReturn(false);
        when(subscriptionRepository.findByStripeSubscriptionId(stripeSubId))
                .thenReturn(Optional.of(localSub));

        Plan plan = buildPlanMock(newPlanId, "pro");
        when(planRepository.findByStripePriceId(priceId)).thenReturn(Optional.of(plan));

        webhookService.handle(event);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        Subscription saved = captor.getValue();
        assertThat(saved.getPlanId()).isEqualTo(newPlanId);
        assertThat(saved.getPendingPlanId()).isNull();
        assertThat(saved.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(saved.isCancelAtPeriodEnd()).isFalse();
        assertThat(saved.getCurrentPeriodStart()).isEqualTo(Instant.ofEpochSecond(100L));
        assertThat(saved.getCurrentPeriodEnd()).isEqualTo(Instant.ofEpochSecond(200L));
    }

    @Test
    @DisplayName("subscriptionUpdated — applies deferred downgrade when pendingPlanId set and period renewed")
    void handleSubscriptionUpdated_deferredDowngrade_appliesPendingPlan() {
        String stripeSubId = "sub_downgrade";
        UUID currentPlanId = UUID.randomUUID();
        UUID pendingPlanId = UUID.randomUUID();

        // currentPeriodStart in the past so newPeriodStart (epoch 200) > old (epoch 100)
        Subscription localSub = buildSubscription(stripeSubId, currentPlanId, pendingPlanId);
        localSub.setCurrentPeriodStart(Instant.ofEpochSecond(100L));

        Event event = mockSubscriptionUpdatedEvent("evt_dwn1", stripeSubId, "active",
                false, "price_starter", epochSecond(200L), epochSecond(300L));

        when(processedWebhookEventRepository.existsByStripeEventId("evt_dwn1")).thenReturn(false);
        when(subscriptionRepository.findByStripeSubscriptionId(stripeSubId))
                .thenReturn(Optional.of(localSub));

        webhookService.handle(event);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        Subscription saved = captor.getValue();
        assertThat(saved.getPlanId()).isEqualTo(pendingPlanId);
        assertThat(saved.getPendingPlanId()).isNull();
        // planRepository should NOT be consulted on the downgrade path
        verify(planRepository, never()).findByStripePriceId(any());
    }

    @Test
    @DisplayName("subscriptionUpdated — pendingPlanId not applied when period has not yet renewed")
    void handleSubscriptionUpdated_pendingPlan_periodNotRenewed_noDowngrade() {
        String stripeSubId = "sub_same_period";
        UUID currentPlanId = UUID.randomUUID();
        UUID pendingPlanId = UUID.randomUUID();
        String priceId = "price_pro";

        // Same currentPeriodStart: no renewal
        Subscription localSub = buildSubscription(stripeSubId, currentPlanId, pendingPlanId);
        localSub.setCurrentPeriodStart(Instant.ofEpochSecond(100L));

        Event event = mockSubscriptionUpdatedEvent("evt_pnd1", stripeSubId, "active",
                false, priceId, epochSecond(100L), epochSecond(200L));

        when(processedWebhookEventRepository.existsByStripeEventId("evt_pnd1")).thenReturn(false);
        when(subscriptionRepository.findByStripeSubscriptionId(stripeSubId))
                .thenReturn(Optional.of(localSub));

        UUID upgradePlanId = UUID.randomUUID();
        Plan upgradePlan = buildPlanMock(upgradePlanId, "pro");
        when(planRepository.findByStripePriceId(priceId)).thenReturn(Optional.of(upgradePlan));

        webhookService.handle(event);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        Subscription saved = captor.getValue();
        // downgrade not applied; planId updated via price lookup instead
        assertThat(saved.getPendingPlanId()).isEqualTo(pendingPlanId);
        assertThat(saved.getPlanId()).isEqualTo(upgradePlanId);
    }

    @Test
    @DisplayName("subscriptionUpdated — syncs cancelAtPeriodEnd and status fields")
    void handleSubscriptionUpdated_syncsCancelAtPeriodEnd() {
        String stripeSubId = "sub_cancel_flag";

        Subscription localSub = buildSubscription(stripeSubId, UUID.randomUUID(), null);
        Event event = mockSubscriptionUpdatedEvent("evt_cancel1", stripeSubId, "active",
                true, "price_pro", epochSecond(100L), epochSecond(200L));

        when(processedWebhookEventRepository.existsByStripeEventId("evt_cancel1")).thenReturn(false);
        when(subscriptionRepository.findByStripeSubscriptionId(stripeSubId))
                .thenReturn(Optional.of(localSub));
        when(planRepository.findByStripePriceId("price_pro")).thenReturn(Optional.empty());

        webhookService.handle(event);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().isCancelAtPeriodEnd()).isTrue();
    }

    @Test
    @DisplayName("subscriptionUpdated — skips when local subscription not found")
    void handleSubscriptionUpdated_noLocalMatch_noSave() {
        String stripeSubId = "sub_missing";
        Event event = mockSubscriptionUpdatedEvent("evt_miss1", stripeSubId, "active",
                false, "price_pro", epochSecond(100L), epochSecond(200L));

        when(processedWebhookEventRepository.existsByStripeEventId("evt_miss1")).thenReturn(false);
        when(subscriptionRepository.findByStripeSubscriptionId(stripeSubId))
                .thenReturn(Optional.empty());

        webhookService.handle(event);

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("subscriptionUpdated — deserialization failure logs warning without crashing")
    void handleSubscriptionUpdated_deserializationFails_noException() {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn("evt_deser1");
        when(event.getType()).thenReturn("customer.subscription.updated");
        when(processedWebhookEventRepository.existsByStripeEventId("evt_deser1")).thenReturn(false);

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.empty());
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);

        webhookService.handle(event);

        verify(subscriptionRepository, never()).save(any());
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

    /**
     * Builds a full {@code customer.subscription.updated} mock event with period timestamps,
     * status, cancelAtPeriodEnd, and a single subscription item pointing to {@code priceId}.
     * All item-level stubs are lenient to avoid UnnecessaryStubbingException when a test
     * exits before reading item fields (e.g. "not found" path).
     */
    private Event mockSubscriptionUpdatedEvent(
            String eventId,
            String stripeSubId,
            String status,
            boolean cancelAtPeriodEnd,
            String priceId,
            Long periodStart,
            Long periodEnd) {

        Price price = mock(Price.class);
        lenient().when(price.getId()).thenReturn(priceId);

        SubscriptionItem item = mock(SubscriptionItem.class);
        lenient().when(item.getPrice()).thenReturn(price);
        lenient().when(item.getCurrentPeriodStart()).thenReturn(periodStart);
        lenient().when(item.getCurrentPeriodEnd()).thenReturn(periodEnd);

        SubscriptionItemCollection items = mock(SubscriptionItemCollection.class);
        lenient().when(items.getData()).thenReturn(List.of(item));

        com.stripe.model.Subscription stripeSub = mock(com.stripe.model.Subscription.class);
        when(stripeSub.getId()).thenReturn(stripeSubId);
        lenient().when(stripeSub.getStatus()).thenReturn(status);
        lenient().when(stripeSub.getCancelAtPeriodEnd()).thenReturn(cancelAtPeriodEnd);
        lenient().when(stripeSub.getItems()).thenReturn(items);

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(stripeSub));

        Event event = mock(Event.class);
        when(event.getId()).thenReturn(eventId);
        when(event.getType()).thenReturn("customer.subscription.updated");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        return event;
    }

    private Subscription buildSubscription(String stripeSubId, UUID planId, UUID pendingPlanId) {
        Subscription sub = new Subscription();
        sub.setStripeSubscriptionId(stripeSubId);
        sub.setPlanId(planId);
        sub.setPendingPlanId(pendingPlanId);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setUpdatedAt(Instant.now());
        return sub;
    }

    /**
     * Creates a Plan mock. Always assign the result to a variable before passing it to
     * {@code thenReturn()} — never nest this call inside {@code thenReturn(Optional.of(...))}
     * because Mockito treats the inner {@code when(...)} as an unfinished stubbing chain.
     */
    private Plan buildPlanMock(UUID id, String slug) {
        Plan plan = mock(Plan.class);
        when(plan.getId()).thenReturn(id);
        when(plan.getSlug()).thenReturn(slug);
        return plan;
    }

    private Long epochSecond(long second) {
        return second;
    }
}
