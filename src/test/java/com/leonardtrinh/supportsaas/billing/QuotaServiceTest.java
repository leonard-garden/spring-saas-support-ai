package com.leonardtrinh.supportsaas.billing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuotaServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PlanRepository planRepository;

    private QuotaServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new QuotaServiceImpl(subscriptionRepository, planRepository);
    }

    // -------------------------------------------------------------------------
    // Scenario 1: free plan — hard block at limit
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Free plan — hard block, no grace period")
    class FreePlanHardBlock {

        @Test
        @DisplayName("checkKnowledgeBaseQuota blocks when current equals free-plan max")
        void checkKnowledgeBaseQuota_freeAtLimit_throws() {
            UUID businessId = UUID.randomUUID();
            // Free plan: maxKnowledgeBases = 1
            Subscription sub = subscriptionWith(SubscriptionStatus.ACTIVE, planId());
            Plan free = planWith(sub.getPlanId(), "free", 1, 5, 100);

            when(subscriptionRepository.findActiveByBusinessId(businessId))
                    .thenReturn(Optional.of(sub));
            when(planRepository.findById(sub.getPlanId()))
                    .thenReturn(Optional.of(free));

            // currentCount = 1 == max (1) → blocked
            assertThatThrownBy(() -> service.checkKnowledgeBaseQuota(businessId, 1))
                    .isInstanceOf(QuotaExceededException.class)
                    .satisfies(ex -> {
                        QuotaExceededException qe = (QuotaExceededException) ex;
                        org.assertj.core.api.Assertions.assertThat(qe.getMetric()).isEqualTo("knowledge_bases");
                        org.assertj.core.api.Assertions.assertThat(qe.getLimit()).isEqualTo(1L);
                        org.assertj.core.api.Assertions.assertThat(qe.getCurrent()).isEqualTo(1L);
                    });
        }

        @Test
        @DisplayName("checkDocumentQuota blocks when current equals free-plan max")
        void checkDocumentQuota_freeAtLimit_throws() {
            UUID businessId = UUID.randomUUID();
            Subscription sub = subscriptionWith(SubscriptionStatus.PAST_DUE, planId());
            Plan starter = planWith(sub.getPlanId(), "starter", 3, 50, 1000);

            when(subscriptionRepository.findActiveByBusinessId(businessId))
                    .thenReturn(Optional.of(sub));
            when(planRepository.findById(sub.getPlanId()))
                    .thenReturn(Optional.of(starter));

            // PAST_DUE — no grace; currentCount = 50 == max (50) → blocked
            assertThatThrownBy(() -> service.checkDocumentQuota(businessId, 50))
                    .isInstanceOf(QuotaExceededException.class)
                    .satisfies(ex -> {
                        QuotaExceededException qe = (QuotaExceededException) ex;
                        org.assertj.core.api.Assertions.assertThat(qe.getMetric()).isEqualTo("documents_per_kb");
                        org.assertj.core.api.Assertions.assertThat(qe.getLimit()).isEqualTo(50L);
                        org.assertj.core.api.Assertions.assertThat(qe.getCurrent()).isEqualTo(50L);
                    });
        }

        @Test
        @DisplayName("checkMessageQuota blocks when CANCELED and at plan max")
        void checkMessageQuota_canceledAtLimit_throws() {
            UUID businessId = UUID.randomUUID();
            Subscription sub = subscriptionWith(SubscriptionStatus.CANCELED, planId());
            Plan starter = planWith(sub.getPlanId(), "starter", 3, 50, 1000);

            when(subscriptionRepository.findActiveByBusinessId(businessId))
                    .thenReturn(Optional.of(sub));
            when(planRepository.findById(sub.getPlanId()))
                    .thenReturn(Optional.of(starter));

            // CANCELED — no grace; currentCount = 1000 == max (1000) → blocked
            assertThatThrownBy(() -> service.checkMessageQuota(businessId, 1000))
                    .isInstanceOf(QuotaExceededException.class);
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 2: paid plan (ACTIVE) — grace period allows up to floor(max * 1.1)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Paid plan (ACTIVE) — 10% grace allows slightly above max")
    class PaidGraceAllow {

        @Test
        @DisplayName("checkKnowledgeBaseQuota allows when current is within grace period")
        void checkKnowledgeBaseQuota_paidWithinGrace_passes() {
            UUID businessId = UUID.randomUUID();
            // Pro plan: maxKnowledgeBases = 10; grace = floor(10 * 1.1) = 11
            Subscription sub = subscriptionWith(SubscriptionStatus.ACTIVE, planId());
            Plan pro = planWith(sub.getPlanId(), "pro", 10, 500, 10000);

            when(subscriptionRepository.findActiveByBusinessId(businessId))
                    .thenReturn(Optional.of(sub));
            when(planRepository.findById(sub.getPlanId()))
                    .thenReturn(Optional.of(pro));

            // currentCount = 10 < effective (11) → allowed
            assertThatCode(() -> service.checkKnowledgeBaseQuota(businessId, 10))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("checkDocumentQuota allows TRIALING tenant within grace")
        void checkDocumentQuota_trialingWithinGrace_passes() {
            UUID businessId = UUID.randomUUID();
            // Pro plan: maxDocumentsPerKb = 500; grace = floor(500 * 1.1) = 550
            Subscription sub = subscriptionWith(SubscriptionStatus.TRIALING, planId());
            Plan pro = planWith(sub.getPlanId(), "pro", 10, 500, 10000);

            when(subscriptionRepository.findActiveByBusinessId(businessId))
                    .thenReturn(Optional.of(sub));
            when(planRepository.findById(sub.getPlanId()))
                    .thenReturn(Optional.of(pro));

            // currentCount = 500 < effective (550) → allowed
            assertThatCode(() -> service.checkDocumentQuota(businessId, 500))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("checkMessageQuota allows ACTIVE tenant at exactly max (below grace limit)")
        void checkMessageQuota_activeAtPlanMax_passes() {
            UUID businessId = UUID.randomUUID();
            // Starter: maxMessagesPerMonth = 1000; grace = floor(1000 * 1.1) = 1100
            Subscription sub = subscriptionWith(SubscriptionStatus.ACTIVE, planId());
            Plan starter = planWith(sub.getPlanId(), "starter", 3, 50, 1000);

            when(subscriptionRepository.findActiveByBusinessId(businessId))
                    .thenReturn(Optional.of(sub));
            when(planRepository.findById(sub.getPlanId()))
                    .thenReturn(Optional.of(starter));

            // currentCount = 1000 < effective (1100) → allowed
            assertThatCode(() -> service.checkMessageQuota(businessId, 1000))
                    .doesNotThrowAnyException();
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 3: paid plan — grace period exhausted → block
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Paid plan — grace exhausted → block")
    class PaidGraceBlock {

        @Test
        @DisplayName("checkKnowledgeBaseQuota blocks when current reaches grace limit")
        void checkKnowledgeBaseQuota_paidAtGraceLimit_throws() {
            UUID businessId = UUID.randomUUID();
            // Pro: maxKnowledgeBases = 10; grace = floor(10 * 1.1) = 11
            Subscription sub = subscriptionWith(SubscriptionStatus.ACTIVE, planId());
            Plan pro = planWith(sub.getPlanId(), "pro", 10, 500, 10000);

            when(subscriptionRepository.findActiveByBusinessId(businessId))
                    .thenReturn(Optional.of(sub));
            when(planRepository.findById(sub.getPlanId()))
                    .thenReturn(Optional.of(pro));

            // currentCount = 11 == effective (11) → blocked
            assertThatThrownBy(() -> service.checkKnowledgeBaseQuota(businessId, 11))
                    .isInstanceOf(QuotaExceededException.class)
                    .satisfies(ex -> {
                        QuotaExceededException qe = (QuotaExceededException) ex;
                        org.assertj.core.api.Assertions.assertThat(qe.getLimit()).isEqualTo(11L);
                        org.assertj.core.api.Assertions.assertThat(qe.getCurrent()).isEqualTo(11L);
                    });
        }

        @Test
        @DisplayName("checkMessageQuota blocks TRIALING tenant beyond grace limit")
        void checkMessageQuota_trialingBeyondGrace_throws() {
            UUID businessId = UUID.randomUUID();
            // Starter: maxMessagesPerMonth = 1000; grace = 1100
            Subscription sub = subscriptionWith(SubscriptionStatus.TRIALING, planId());
            Plan starter = planWith(sub.getPlanId(), "starter", 3, 50, 1000);

            when(subscriptionRepository.findActiveByBusinessId(businessId))
                    .thenReturn(Optional.of(sub));
            when(planRepository.findById(sub.getPlanId()))
                    .thenReturn(Optional.of(starter));

            // currentCount = 1100 >= effective (1100) → blocked
            assertThatThrownBy(() -> service.checkMessageQuota(businessId, 1100))
                    .isInstanceOf(QuotaExceededException.class)
                    .satisfies(ex -> {
                        QuotaExceededException qe = (QuotaExceededException) ex;
                        org.assertj.core.api.Assertions.assertThat(qe.getMetric()).isEqualTo("messages_per_month");
                        org.assertj.core.api.Assertions.assertThat(qe.getLimit()).isEqualTo(1100L);
                    });
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 4: business plan — unlimited, always allow
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Business plan — unlimited, all quota checks short-circuit to allow")
    class BusinessUnlimited {

        @Test
        @DisplayName("checkKnowledgeBaseQuota always allows for business plan")
        void checkKnowledgeBaseQuota_businessPlan_alwaysAllows() {
            UUID businessId = UUID.randomUUID();
            Subscription sub = subscriptionWith(SubscriptionStatus.ACTIVE, planId());
            // business plan: any numeric limits are irrelevant
            Plan business = planWith(sub.getPlanId(), "business", 999, 999, 999);

            when(subscriptionRepository.findActiveByBusinessId(businessId))
                    .thenReturn(Optional.of(sub));
            when(planRepository.findById(sub.getPlanId()))
                    .thenReturn(Optional.of(business));

            // Even with an absurdly high count, the business plan short-circuits
            assertThatCode(() -> service.checkKnowledgeBaseQuota(businessId, Long.MAX_VALUE))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("checkDocumentQuota always allows for business plan")
        void checkDocumentQuota_businessPlan_alwaysAllows() {
            UUID businessId = UUID.randomUUID();
            Subscription sub = subscriptionWith(SubscriptionStatus.ACTIVE, planId());
            Plan business = planWith(sub.getPlanId(), "business", 999, 999, 999);

            when(subscriptionRepository.findActiveByBusinessId(businessId))
                    .thenReturn(Optional.of(sub));
            when(planRepository.findById(sub.getPlanId()))
                    .thenReturn(Optional.of(business));

            assertThatCode(() -> service.checkDocumentQuota(businessId, Long.MAX_VALUE))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("checkMessageQuota always allows for business plan")
        void checkMessageQuota_businessPlan_alwaysAllows() {
            UUID businessId = UUID.randomUUID();
            Subscription sub = subscriptionWith(SubscriptionStatus.ACTIVE, planId());
            Plan business = planWith(sub.getPlanId(), "business", 999, 999, 999);

            when(subscriptionRepository.findActiveByBusinessId(businessId))
                    .thenReturn(Optional.of(sub));
            when(planRepository.findById(sub.getPlanId()))
                    .thenReturn(Optional.of(business));

            assertThatCode(() -> service.checkMessageQuota(businessId, Long.MAX_VALUE))
                    .doesNotThrowAnyException();
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 5: no active subscription — fallback to free plan
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("No active subscription — fallback to free plan limits")
    class NoSubscriptionFallback {

        @Test
        @DisplayName("checkKnowledgeBaseQuota falls back to free plan and blocks at limit")
        void checkKnowledgeBaseQuota_noSubscription_fallsBackToFree() {
            UUID businessId = UUID.randomUUID();
            UUID freePlanId = planId();
            Plan free = planWith(freePlanId, "free", 1, 5, 100);

            when(subscriptionRepository.findActiveByBusinessId(businessId))
                    .thenReturn(Optional.empty());
            when(planRepository.findBySlug("free"))
                    .thenReturn(Optional.of(free));

            // currentCount = 1 == free max (1) → blocked with no grace
            assertThatThrownBy(() -> service.checkKnowledgeBaseQuota(businessId, 1))
                    .isInstanceOf(QuotaExceededException.class)
                    .satisfies(ex -> {
                        QuotaExceededException qe = (QuotaExceededException) ex;
                        org.assertj.core.api.Assertions.assertThat(qe.getLimit()).isEqualTo(1L);
                    });
        }

        @Test
        @DisplayName("checkKnowledgeBaseQuota falls back to free plan and allows below limit")
        void checkKnowledgeBaseQuota_noSubscription_allowsBelowFreeLimit() {
            UUID businessId = UUID.randomUUID();
            UUID freePlanId = planId();
            Plan free = planWith(freePlanId, "free", 1, 5, 100);

            when(subscriptionRepository.findActiveByBusinessId(businessId))
                    .thenReturn(Optional.empty());
            when(planRepository.findBySlug("free"))
                    .thenReturn(Optional.of(free));

            // currentCount = 0 < free max (1) → allowed
            assertThatCode(() -> service.checkKnowledgeBaseQuota(businessId, 0))
                    .doesNotThrowAnyException();
        }
    }

    // -------------------------------------------------------------------------
    // Helper factories
    // -------------------------------------------------------------------------

    private UUID planId() {
        return UUID.randomUUID();
    }

    private Subscription subscriptionWith(SubscriptionStatus status, UUID planId) {
        Subscription sub = new Subscription();
        sub.setBusinessId(UUID.randomUUID());
        sub.setPlanId(planId);
        sub.setStatus(status);
        return sub;
    }

    private Plan planWith(UUID id, String slug, int maxKbs, int maxDocs, int maxMsgs) {
        // Use reflection-free approach: set via a test-only subclass
        return new TestPlan(id, slug, maxKbs, maxDocs, maxMsgs);
    }

    /**
     * Test-only subclass that allows constructing a Plan with all fields set.
     * Plan has no all-args constructor (no Lombok), so we extend here in test scope.
     */
    private static class TestPlan extends Plan {
        TestPlan(UUID id, String slug, int maxKbs, int maxDocs, int maxMsgs) {
            // Plan fields are private — use reflection to set them
            try {
                setField(this, "id", id);
                setField(this, "slug", slug);
                setField(this, "maxKnowledgeBases", maxKbs);
                setField(this, "maxDocumentsPerKb", maxDocs);
                setField(this, "maxMessagesPerMonth", maxMsgs);
                setField(this, "maxMembers", 10);
                setField(this, "name", slug);
                setField(this, "priceUsdMonthly", BigDecimal.ZERO);
                setField(this, "isActive", true);
            } catch (Exception e) {
                throw new RuntimeException("TestPlan setup failed", e);
            }
        }

        private void setField(Object target, String fieldName, Object value)
                throws NoSuchFieldException, IllegalAccessException {
            java.lang.reflect.Field f = Plan.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        }
    }
}
