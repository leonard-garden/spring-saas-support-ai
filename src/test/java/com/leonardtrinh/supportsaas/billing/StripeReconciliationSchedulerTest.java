package com.leonardtrinh.supportsaas.billing;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StripeReconciliationSchedulerTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private StripeService stripeService;

    private SimpleMeterRegistry meterRegistry;
    private StripeReconciliationScheduler scheduler;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        scheduler = new StripeReconciliationScheduler(
                subscriptionRepository, subscriptionService, stripeService, meterRegistry);
    }

    private Subscription buildSubscription(String stripeSubId, SubscriptionStatus status) {
        Subscription sub = new Subscription();
        sub.setBusinessId(UUID.randomUUID());
        sub.setStatus(status);
        sub.setStripeSubscriptionId(stripeSubId);
        sub.setUpdatedAt(Instant.now());
        return sub;
    }

    private com.stripe.model.Subscription stripeSubWithStatus(String status) {
        com.stripe.model.Subscription stripeSub = new com.stripe.model.Subscription();
        stripeSub.setStatus(status);
        return stripeSub;
    }

    @Test
    @DisplayName("reconcile — mismatch detected calls syncFromStripe and increments counter")
    void reconcile_mismatchDetected_callsSyncAndIncrementsCounter() {
        Subscription sub = buildSubscription("sub_123", SubscriptionStatus.ACTIVE);

        when(subscriptionRepository.findAllByStatusIn(
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE, SubscriptionStatus.TRIALING)))
                .thenReturn(List.of(sub));
        when(stripeService.retrieveSubscription("sub_123"))
                .thenReturn(stripeSubWithStatus("past_due")); // DB says ACTIVE, Stripe says PAST_DUE

        scheduler.reconcile();

        verify(subscriptionService).syncFromStripe(sub);
        assertThat(meterRegistry.counter("stripe.reconciliation.mismatch").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("reconcile — no mismatch does not call syncFromStripe")
    void reconcile_noMismatch_doesNotSync() {
        Subscription sub = buildSubscription("sub_456", SubscriptionStatus.ACTIVE);

        when(subscriptionRepository.findAllByStatusIn(any()))
                .thenReturn(List.of(sub));
        when(stripeService.retrieveSubscription("sub_456"))
                .thenReturn(stripeSubWithStatus("active")); // both match

        scheduler.reconcile();

        verify(subscriptionService, never()).syncFromStripe(any());
        assertThat(meterRegistry.counter("stripe.reconciliation.mismatch").count()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("reconcile — subscription without stripeSubscriptionId is skipped")
    void reconcile_noStripeId_isSkipped() {
        Subscription sub = buildSubscription(null, SubscriptionStatus.ACTIVE);

        when(subscriptionRepository.findAllByStatusIn(any()))
                .thenReturn(List.of(sub));

        scheduler.reconcile();

        verify(stripeService, never()).retrieveSubscription(any());
        verify(subscriptionService, never()).syncFromStripe(any());
    }

    @Test
    @DisplayName("reconcile — empty candidate list does nothing")
    void reconcile_noCandidates_doesNothing() {
        when(subscriptionRepository.findAllByStatusIn(any())).thenReturn(List.of());

        scheduler.reconcile();

        verify(stripeService, never()).retrieveSubscription(any());
        verify(subscriptionService, never()).syncFromStripe(any());
    }

    @Test
    @DisplayName("reconcile — Stripe API error for one subscription does not abort remaining")
    void reconcile_stripeErrorForOne_continuesProcessingRest() {
        Subscription sub1 = buildSubscription("sub_err", SubscriptionStatus.ACTIVE);
        Subscription sub2 = buildSubscription("sub_ok", SubscriptionStatus.TRIALING);

        when(subscriptionRepository.findAllByStatusIn(any()))
                .thenReturn(List.of(sub1, sub2));
        when(stripeService.retrieveSubscription("sub_err"))
                .thenThrow(new RuntimeException("Stripe timeout"));
        when(stripeService.retrieveSubscription("sub_ok"))
                .thenReturn(stripeSubWithStatus("past_due")); // mismatch

        scheduler.reconcile();

        // sub1 errored but sub2 still reconciled
        verify(subscriptionService).syncFromStripe(sub2);
        assertThat(meterRegistry.counter("stripe.reconciliation.mismatch").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("reconcile — multiple mismatches increment counter for each")
    void reconcile_multipleMismatches_counterIncrementedForEach() {
        Subscription sub1 = buildSubscription("sub_a", SubscriptionStatus.ACTIVE);
        Subscription sub2 = buildSubscription("sub_b", SubscriptionStatus.TRIALING);

        when(subscriptionRepository.findAllByStatusIn(any()))
                .thenReturn(List.of(sub1, sub2));
        when(stripeService.retrieveSubscription("sub_a"))
                .thenReturn(stripeSubWithStatus("past_due"));
        when(stripeService.retrieveSubscription("sub_b"))
                .thenReturn(stripeSubWithStatus("canceled"));

        scheduler.reconcile();

        verify(subscriptionService, times(2)).syncFromStripe(any());
        assertThat(meterRegistry.counter("stripe.reconciliation.mismatch").count()).isEqualTo(2.0);
    }
}
