package com.leonardtrinh.supportsaas.billing;

import com.leonardtrinh.supportsaas.email.AsyncEmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrialExpirySchedulerTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private AsyncEmailSender asyncEmailSender;

    private TrialExpiryScheduler scheduler;

    @BeforeEach
    void setUp() {
        io.micrometer.core.instrument.simple.SimpleMeterRegistry meterRegistry =
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        scheduler = new TrialExpiryScheduler(subscriptionRepository, planRepository, asyncEmailSender, meterRegistry);
    }

    private Subscription buildExpiredTrialSubscription(UUID businessId, UUID planId) {
        Subscription sub = new Subscription();
        sub.setBusinessId(businessId);
        sub.setPlanId(planId);
        sub.setStatus(SubscriptionStatus.TRIALING);
        sub.setTrialEndsAt(Instant.now().minus(1, ChronoUnit.DAYS));
        sub.setUpdatedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        return sub;
    }

    private Plan buildFreePlan(UUID planId) {
        Plan plan = new Plan();
        // Use ReflectionTestUtils to set the private id field
        org.springframework.test.util.ReflectionTestUtils.setField(plan, "id", planId);
        org.springframework.test.util.ReflectionTestUtils.setField(plan, "slug", "free");
        org.springframework.test.util.ReflectionTestUtils.setField(plan, "name", "Free");
        return plan;
    }

    @Test
    @DisplayName("downgradeExpiredTrials — downgrades each expired trial to Free and sends email")
    void downgradeExpiredTrials_withExpiredTrials_downgradesToFreeAndSendsEmail() {
        UUID businessId = UUID.randomUUID();
        UUID freePlanId = UUID.randomUUID();

        Subscription sub = buildExpiredTrialSubscription(businessId, UUID.randomUUID());
        Plan freePlan = buildFreePlan(freePlanId);

        when(subscriptionRepository.findAllByStatusAndTrialEndsAtBefore(
                eq(SubscriptionStatus.TRIALING), any(Instant.class)))
                .thenReturn(List.of(sub));
        when(planRepository.findBySlug("free")).thenReturn(Optional.of(freePlan));
        when(subscriptionRepository.findOwnerEmailByBusinessId(businessId))
                .thenReturn(Optional.of("owner@example.com"));

        scheduler.downgradeExpiredTrials();

        // Subscription status updated to ACTIVE (Free)
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(sub.getPlanId()).isEqualTo(freePlanId);
        verify(subscriptionRepository).save(sub);

        // Email sent asynchronously
        verify(asyncEmailSender).sendTrialExpiredAsync("owner@example.com");
    }

    @Test
    @DisplayName("downgradeExpiredTrials — processes multiple expired trials")
    void downgradeExpiredTrials_multipleExpired_downgradedAll() {
        UUID businessId1 = UUID.randomUUID();
        UUID businessId2 = UUID.randomUUID();
        UUID freePlanId = UUID.randomUUID();

        Subscription sub1 = buildExpiredTrialSubscription(businessId1, UUID.randomUUID());
        Subscription sub2 = buildExpiredTrialSubscription(businessId2, UUID.randomUUID());
        Plan freePlan = buildFreePlan(freePlanId);

        when(subscriptionRepository.findAllByStatusAndTrialEndsAtBefore(
                eq(SubscriptionStatus.TRIALING), any(Instant.class)))
                .thenReturn(List.of(sub1, sub2));
        when(planRepository.findBySlug("free")).thenReturn(Optional.of(freePlan));
        when(subscriptionRepository.findOwnerEmailByBusinessId(any())).thenReturn(Optional.empty());

        scheduler.downgradeExpiredTrials();

        assertThat(sub1.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(sub2.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(subscriptionRepository, times(2)).save(any(Subscription.class));
    }

    @Test
    @DisplayName("downgradeExpiredTrials — no expired trials skips all processing")
    void downgradeExpiredTrials_noExpiredTrials_doesNothing() {
        when(subscriptionRepository.findAllByStatusAndTrialEndsAtBefore(
                eq(SubscriptionStatus.TRIALING), any(Instant.class)))
                .thenReturn(List.of());

        scheduler.downgradeExpiredTrials();

        verify(planRepository, never()).findBySlug(any());
        verify(subscriptionRepository, never()).save(any());
        verify(asyncEmailSender, never()).sendTrialExpiredAsync(any());
    }

    @Test
    @DisplayName("downgradeExpiredTrials — Free plan missing aborts without saving")
    void downgradeExpiredTrials_freePlanMissing_abortsGracefully() {
        Subscription sub = buildExpiredTrialSubscription(UUID.randomUUID(), UUID.randomUUID());

        when(subscriptionRepository.findAllByStatusAndTrialEndsAtBefore(
                eq(SubscriptionStatus.TRIALING), any(Instant.class)))
                .thenReturn(List.of(sub));
        when(planRepository.findBySlug("free")).thenReturn(Optional.empty());

        scheduler.downgradeExpiredTrials();

        verify(subscriptionRepository, never()).save(any());
        verify(asyncEmailSender, never()).sendTrialExpiredAsync(any());
    }

    @Test
    @DisplayName("downgradeExpiredTrials — owner email missing still downgrades subscription")
    void downgradeExpiredTrials_ownerEmailMissing_stillDowngrades() {
        UUID businessId = UUID.randomUUID();
        UUID freePlanId = UUID.randomUUID();

        Subscription sub = buildExpiredTrialSubscription(businessId, UUID.randomUUID());
        Plan freePlan = buildFreePlan(freePlanId);

        when(subscriptionRepository.findAllByStatusAndTrialEndsAtBefore(
                eq(SubscriptionStatus.TRIALING), any(Instant.class)))
                .thenReturn(List.of(sub));
        when(planRepository.findBySlug("free")).thenReturn(Optional.of(freePlan));
        when(subscriptionRepository.findOwnerEmailByBusinessId(businessId))
                .thenReturn(Optional.empty());

        scheduler.downgradeExpiredTrials();

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(subscriptionRepository).save(sub);
        verify(asyncEmailSender, never()).sendTrialExpiredAsync(any());
    }

    @Test
    @DisplayName("downgradeExpiredTrials — is idempotent; re-run finds no rows on second pass")
    void downgradeExpiredTrials_idempotent_secondRunFindsNoRows() {
        UUID freePlanId = UUID.randomUUID();
        Subscription sub = buildExpiredTrialSubscription(UUID.randomUUID(), UUID.randomUUID());
        Plan freePlan = buildFreePlan(freePlanId);

        when(subscriptionRepository.findAllByStatusAndTrialEndsAtBefore(
                eq(SubscriptionStatus.TRIALING), any(Instant.class)))
                .thenReturn(List.of(sub))
                .thenReturn(List.of()); // second call returns empty
        when(planRepository.findBySlug("free")).thenReturn(Optional.of(freePlan));
        when(subscriptionRepository.findOwnerEmailByBusinessId(any())).thenReturn(Optional.empty());

        scheduler.downgradeExpiredTrials();
        scheduler.downgradeExpiredTrials(); // second run

        verify(subscriptionRepository, times(1)).save(any(Subscription.class));
    }
}
