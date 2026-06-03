package com.leonardtrinh.supportsaas.billing;

import com.leonardtrinh.supportsaas.tenant.Business;
import com.leonardtrinh.supportsaas.tenant.BusinessRepository;
import com.stripe.model.SubscriptionSchedule;
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
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Boundary-validation tests for upgrade and downgrade flows in SubscriptionServiceImpl.
 *
 * <p>Complements SubscriptionServiceTest by focusing exclusively on the guard conditions
 * that protect the upgrade/downgrade paths, the exact exception messages, and the
 * "deferred, not immediately applied" contract for downgrades.
 */
@ExtendWith(MockitoExtension.class)
class UpgradeDowngradeTest {

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
        service = new SubscriptionServiceImpl(
                subscriptionRepository, planRepository,
                businessRepository, stripeService,
                "http://localhost:8081");
    }

    // -----------------------------------------------------------------------
    // Upgrade guard conditions
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("upgradeSubscription — guard conditions")
    class UpgradeGuards {

        @Test
        @DisplayName("upgrade to lower-priced plan throws CannotUpgradeException")
        void upgrade_toLowerPricedPlan_throws() {
            UUID businessId = UUID.randomUUID();
            UUID currentPlanId = UUID.randomUUID();
            UUID targetPlanId = UUID.randomUUID();

            Subscription sub = subWith(currentPlanId, "sub_stripe_001");
            Plan current = planWithPrice(currentPlanId, "pro",     "price_pro",     new BigDecimal("99.00"));
            Plan target  = planWithPrice(targetPlanId, "starter", "price_starter", new BigDecimal("29.00"));

            when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.of(sub));
            when(planRepository.findBySlug("starter")).thenReturn(Optional.of(target));
            when(planRepository.findById(currentPlanId)).thenReturn(Optional.of(current));

            assertThatThrownBy(() -> service.upgradeSubscription(businessId, "starter"))
                    .isInstanceOf(CannotUpgradeException.class)
                    .hasMessageContaining("higher than current plan price");

            verify(stripeService, never()).updateSubscription(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("upgrade to same-priced plan throws CannotUpgradeException")
        void upgrade_toSamePricedPlan_throws() {
            UUID businessId = UUID.randomUUID();
            UUID currentPlanId = UUID.randomUUID();
            UUID targetPlanId = UUID.randomUUID();

            Subscription sub = subWith(currentPlanId, "sub_stripe_002");
            Plan current = planWithPrice(currentPlanId, "pro",  "price_pro",  new BigDecimal("99.00"));
            Plan target  = planWithPrice(targetPlanId, "pro2", "price_pro2", new BigDecimal("99.00"));

            when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.of(sub));
            when(planRepository.findBySlug("pro2")).thenReturn(Optional.of(target));
            when(planRepository.findById(currentPlanId)).thenReturn(Optional.of(current));

            assertThatThrownBy(() -> service.upgradeSubscription(businessId, "pro2"))
                    .isInstanceOf(CannotUpgradeException.class)
                    .hasMessageContaining("higher than current plan price");

            verify(stripeService, never()).updateSubscription(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("upgrade when pendingPlanId already set still proceeds (upgrade overrides pending downgrade)")
        void upgrade_withPendingDowngrade_callsStripe() {
            // The service does NOT block upgrades when pendingPlanId is set —
            // only downgrade blocks on a pending downgrade. Verify the happy path still works.
            UUID businessId = UUID.randomUUID();
            UUID currentPlanId = UUID.randomUUID();
            UUID targetPlanId = UUID.randomUUID();

            Subscription sub = subWith(currentPlanId, "sub_stripe_003");
            sub.setPendingPlanId(UUID.randomUUID()); // a downgrade was already scheduled

            Plan current = planWithPrice(currentPlanId, "starter", "price_starter", new BigDecimal("29.00"));
            Plan target  = planWithPrice(targetPlanId, "pro",     "price_pro",     new BigDecimal("99.00"));
            ReflectionTestUtils.setField(target, "name", "Pro");

            com.stripe.model.Subscription stripeResult = new com.stripe.model.Subscription();
            when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.of(sub));
            when(planRepository.findBySlug("pro")).thenReturn(Optional.of(target));
            when(planRepository.findById(currentPlanId)).thenReturn(Optional.of(current));
            when(stripeService.updateSubscription(anyString(), anyString(), anyString()))
                    .thenReturn(stripeResult);

            UpgradeResponse response = service.upgradeSubscription(businessId, "pro");

            assertThat(response.targetPlan()).isEqualTo("Pro");
            verify(stripeService).updateSubscription(eq("sub_stripe_003"), eq("price_pro"), anyString());
        }

        @Test
        @DisplayName("valid upgrade calls StripeService.updateSubscription with correct args")
        void upgrade_validPath_callsStripeWithCorrectArgs() {
            UUID businessId = UUID.randomUUID();
            UUID currentPlanId = UUID.randomUUID();
            UUID targetPlanId = UUID.randomUUID();

            Subscription sub = subWith(currentPlanId, "sub_stripe_004");
            Plan current = planWithPrice(currentPlanId, "starter", "price_starter", new BigDecimal("29.00"));
            Plan target  = planWithPrice(targetPlanId, "pro",     "price_pro",     new BigDecimal("99.00"));
            ReflectionTestUtils.setField(target, "name", "Pro");

            com.stripe.model.Subscription stripeResult = new com.stripe.model.Subscription();
            when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.of(sub));
            when(planRepository.findBySlug("pro")).thenReturn(Optional.of(target));
            when(planRepository.findById(currentPlanId)).thenReturn(Optional.of(current));
            when(stripeService.updateSubscription(anyString(), anyString(), anyString()))
                    .thenReturn(stripeResult);

            UpgradeResponse response = service.upgradeSubscription(businessId, "pro");

            assertThat(response.targetPlan()).isEqualTo("Pro");
            assertThat(response.message()).isNotBlank();
            verify(stripeService).updateSubscription(
                    eq("sub_stripe_004"), eq("price_pro"), anyString());
        }
    }

    // -----------------------------------------------------------------------
    // Downgrade guard conditions
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("downgradeSubscription — guard conditions")
    class DowngradeGuards {

        @Test
        @DisplayName("downgrade when pendingPlanId already set throws CannotDowngradeException")
        void downgrade_pendingAlreadySet_throws() {
            UUID businessId = UUID.randomUUID();
            UUID planId = UUID.randomUUID();

            Subscription sub = subWith(planId, "sub_stripe_010");
            sub.setPendingPlanId(UUID.randomUUID()); // already a pending downgrade

            when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.of(sub));

            assertThatThrownBy(() -> service.downgradeSubscription(businessId, "free"))
                    .isInstanceOf(CannotDowngradeException.class)
                    .hasMessageContaining("already scheduled");

            verify(stripeService, never()).scheduleSubscriptionUpdate(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("downgrade to higher-priced plan throws CannotDowngradeException")
        void downgrade_toHigherPricedPlan_throws() {
            UUID businessId = UUID.randomUUID();
            UUID currentPlanId = UUID.randomUUID();
            UUID targetPlanId = UUID.randomUUID();

            Subscription sub = subWith(currentPlanId, "sub_stripe_011");
            Plan current = planWithPrice(currentPlanId, "starter", "price_starter", new BigDecimal("29.00"));
            Plan target  = planWithPrice(targetPlanId, "pro",     "price_pro",     new BigDecimal("99.00"));

            when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.of(sub));
            when(planRepository.findBySlug("pro")).thenReturn(Optional.of(target));
            when(planRepository.findById(currentPlanId)).thenReturn(Optional.of(current));

            assertThatThrownBy(() -> service.downgradeSubscription(businessId, "pro"))
                    .isInstanceOf(CannotDowngradeException.class)
                    .hasMessageContaining("lower than current plan price");

            verify(stripeService, never()).scheduleSubscriptionUpdate(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("downgrade to same-priced plan throws CannotDowngradeException")
        void downgrade_toSamePricedPlan_throws() {
            UUID businessId = UUID.randomUUID();
            UUID currentPlanId = UUID.randomUUID();
            UUID targetPlanId = UUID.randomUUID();

            Subscription sub = subWith(currentPlanId, "sub_stripe_012");
            Plan current = planWithPrice(currentPlanId, "pro",  "price_pro",  new BigDecimal("99.00"));
            Plan target  = planWithPrice(targetPlanId, "pro2", "price_pro2", new BigDecimal("99.00"));

            when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.of(sub));
            when(planRepository.findBySlug("pro2")).thenReturn(Optional.of(target));
            when(planRepository.findById(currentPlanId)).thenReturn(Optional.of(current));

            assertThatThrownBy(() -> service.downgradeSubscription(businessId, "pro2"))
                    .isInstanceOf(CannotDowngradeException.class)
                    .hasMessageContaining("lower than current plan price");

            verify(stripeService, never()).scheduleSubscriptionUpdate(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("valid downgrade schedules via Stripe and sets pendingPlanId — NOT immediately applied")
        void downgrade_validPath_schedulesDeferredAndSetsPendingPlanId() {
            UUID businessId = UUID.randomUUID();
            UUID currentPlanId = UUID.randomUUID();
            UUID targetPlanId = UUID.randomUUID();
            Instant periodEnd = Instant.parse("2026-08-01T00:00:00Z");

            Subscription sub = subWith(currentPlanId, "sub_stripe_013");
            sub.setCurrentPeriodEnd(periodEnd);

            Plan current = planWithPrice(currentPlanId, "pro",     "price_pro",     new BigDecimal("99.00"));
            Plan target  = planWithPrice(targetPlanId, "starter", "price_starter", new BigDecimal("29.00"));
            ReflectionTestUtils.setField(target, "name", "Starter");

            SubscriptionSchedule schedule = new SubscriptionSchedule();
            when(subscriptionRepository.findActiveByBusinessId(businessId))
                    .thenReturn(Optional.of(sub));
            when(planRepository.findBySlug("starter")).thenReturn(Optional.of(target));
            when(planRepository.findById(currentPlanId)).thenReturn(Optional.of(current));
            when(stripeService.scheduleSubscriptionUpdate(anyString(), anyString(), anyString()))
                    .thenReturn(schedule);
            when(subscriptionRepository.save(any(Subscription.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            DowngradeResponse response = service.downgradeSubscription(businessId, "starter");

            // Response carries the future plan and the effective date (end of current period)
            assertThat(response.targetPlan()).isEqualTo("Starter");
            assertThat(response.effectiveAt()).isEqualTo(periodEnd);
            assertThat(response.message()).isNotBlank();

            // Stripe scheduleSubscriptionUpdate was called (not updateSubscription — not immediate)
            verify(stripeService).scheduleSubscriptionUpdate(
                    eq("sub_stripe_013"), eq("price_starter"), anyString());
            verify(stripeService, never()).updateSubscription(anyString(), anyString(), anyString());

            // pendingPlanId was saved on the subscription record
            ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
            verify(subscriptionRepository).save(captor.capture());
            assertThat(captor.getValue().getPendingPlanId()).isEqualTo(targetPlanId);

            // The subscription's planId must NOT have changed (still the current plan)
            assertThat(captor.getValue().getPlanId()).isEqualTo(currentPlanId);
        }

        @Test
        @DisplayName("valid downgrade: planId on subscription is unchanged until webhook fires")
        void downgrade_validPath_planIdUnchangedAfterSchedule() {
            UUID businessId = UUID.randomUUID();
            UUID currentPlanId = UUID.randomUUID();
            UUID targetPlanId = UUID.randomUUID();

            Subscription sub = subWith(currentPlanId, "sub_stripe_014");
            sub.setCurrentPeriodEnd(Instant.now().plusSeconds(86_400));

            Plan current = planWithPrice(currentPlanId, "business", "price_biz",     new BigDecimal("299.00"));
            Plan target  = planWithPrice(targetPlanId, "pro",      "price_pro",     new BigDecimal("99.00"));
            ReflectionTestUtils.setField(target, "name", "Pro");

            when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.of(sub));
            when(planRepository.findBySlug("pro")).thenReturn(Optional.of(target));
            when(planRepository.findById(currentPlanId)).thenReturn(Optional.of(current));
            when(stripeService.scheduleSubscriptionUpdate(anyString(), anyString(), anyString()))
                    .thenReturn(new SubscriptionSchedule());
            when(subscriptionRepository.save(any(Subscription.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            service.downgradeSubscription(businessId, "pro");

            ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
            verify(subscriptionRepository).save(captor.capture());

            Subscription saved = captor.getValue();
            // planId is still the old plan — only pendingPlanId changed
            assertThat(saved.getPlanId()).isEqualTo(currentPlanId);
            assertThat(saved.getPendingPlanId()).isEqualTo(targetPlanId);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Subscription subWith(UUID planId, String stripeSubId) {
        Subscription sub = new Subscription();
        sub.setPlanId(planId);
        sub.setStripeSubscriptionId(stripeSubId);
        return sub;
    }

    private Plan planWithPrice(UUID id, String slug, String stripePriceId, BigDecimal price) {
        Plan plan = new Plan();
        ReflectionTestUtils.setField(plan, "id", id);
        ReflectionTestUtils.setField(plan, "slug", slug);
        ReflectionTestUtils.setField(plan, "stripePriceId", stripePriceId);
        ReflectionTestUtils.setField(plan, "priceUsdMonthly", price);
        return plan;
    }
}
