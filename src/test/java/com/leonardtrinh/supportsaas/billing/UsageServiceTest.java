package com.leonardtrinh.supportsaas.billing;

import com.leonardtrinh.supportsaas.chat.MessageUsage;
import com.leonardtrinh.supportsaas.chat.MessageUsageRepository;
import com.leonardtrinh.supportsaas.document.DocumentRepository;
import com.leonardtrinh.supportsaas.knowledgebase.KnowledgeBaseRepository;
import com.leonardtrinh.supportsaas.member.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsageServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MessageUsageRepository messageUsageRepository;

    private UsageServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UsageServiceImpl(
                subscriptionRepository,
                planRepository,
                knowledgeBaseRepository,
                documentRepository,
                memberRepository,
                messageUsageRepository);
    }

    @Test
    @DisplayName("getUsage returns live counts with plan limits for active subscription")
    void getUsage_withActivePlan_returnsLiveCount() {
        UUID businessId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        Subscription sub = subscriptionWith(planId);
        Plan plan = planWith(planId, "starter", 3, 50, 1000, 3);

        when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.of(sub));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(knowledgeBaseRepository.countByBusinessId(businessId)).thenReturn(2L);
        when(documentRepository.countByBusinessId(businessId)).thenReturn(15L);
        when(memberRepository.countByBusinessId(businessId)).thenReturn(2L);
        when(messageUsageRepository.findByBusinessIdAndYearMonth(eq(businessId), any()))
                .thenReturn(Optional.of(messageUsageWith(42)));

        UsageResponse result = service.getUsage(businessId);

        assertThat(result.knowledgeBases().used()).isEqualTo(2L);
        assertThat(result.knowledgeBases().limit()).isEqualTo(3L);
        assertThat(result.documents().used()).isEqualTo(15L);
        assertThat(result.documents().limit()).isEqualTo(50L);
        assertThat(result.messages().used()).isEqualTo(42L);
        assertThat(result.messages().limit()).isEqualTo(1000L);
        assertThat(result.members().used()).isEqualTo(2L);
        assertThat(result.members().limit()).isEqualTo(3L);
    }

    @Test
    @DisplayName("getUsage returns -1 for unlimited fields on business plan")
    void getUsage_withBusinessPlan_returnsMinusOneForUnlimitedFields() {
        UUID businessId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        Subscription sub = subscriptionWith(planId);
        Plan plan = planWith(planId, "business", 999, 999, 100000, 999);

        when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.of(sub));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(knowledgeBaseRepository.countByBusinessId(businessId)).thenReturn(5L);
        when(documentRepository.countByBusinessId(businessId)).thenReturn(200L);
        when(memberRepository.countByBusinessId(businessId)).thenReturn(10L);
        when(messageUsageRepository.findByBusinessIdAndYearMonth(eq(businessId), any()))
                .thenReturn(Optional.empty());

        UsageResponse result = service.getUsage(businessId);

        assertThat(result.knowledgeBases().limit()).isEqualTo(-1L);
        assertThat(result.documents().limit()).isEqualTo(-1L);
        assertThat(result.members().limit()).isEqualTo(-1L);
        assertThat(result.messages().limit()).isEqualTo(100000L);
        assertThat(result.messages().used()).isEqualTo(0L);
    }

    @Test
    @DisplayName("getUsage falls back to free plan limits when no active subscription exists")
    void getUsage_withNoSubscription_fallsBackToFreePlan() {
        UUID businessId = UUID.randomUUID();
        UUID freePlanId = UUID.randomUUID();

        Plan freePlan = planWith(freePlanId, "free", 1, 5, 100, 1);

        when(subscriptionRepository.findActiveByBusinessId(businessId)).thenReturn(Optional.empty());
        when(planRepository.findBySlug("free")).thenReturn(Optional.of(freePlan));
        when(knowledgeBaseRepository.countByBusinessId(businessId)).thenReturn(0L);
        when(documentRepository.countByBusinessId(businessId)).thenReturn(0L);
        when(memberRepository.countByBusinessId(businessId)).thenReturn(1L);
        when(messageUsageRepository.findByBusinessIdAndYearMonth(eq(businessId), any()))
                .thenReturn(Optional.empty());

        UsageResponse result = service.getUsage(businessId);

        assertThat(result.knowledgeBases().limit()).isEqualTo(1L);
        assertThat(result.documents().limit()).isEqualTo(5L);
        assertThat(result.messages().limit()).isEqualTo(100L);
        assertThat(result.members().limit()).isEqualTo(1L);
        assertThat(result.members().used()).isEqualTo(1L);
        assertThat(result.messages().used()).isEqualTo(0L);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Subscription subscriptionWith(UUID planId) {
        Subscription sub = new Subscription();
        sub.setPlanId(planId);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        return sub;
    }

    private Plan planWith(UUID id, String slug, int maxKbs, int maxDocs, int maxMessages, int maxMembers) {
        Plan plan = new Plan();
        org.springframework.test.util.ReflectionTestUtils.setField(plan, "id", id);
        org.springframework.test.util.ReflectionTestUtils.setField(plan, "slug", slug);
        org.springframework.test.util.ReflectionTestUtils.setField(plan, "maxKnowledgeBases", maxKbs);
        org.springframework.test.util.ReflectionTestUtils.setField(plan, "maxDocumentsPerKb", maxDocs);
        org.springframework.test.util.ReflectionTestUtils.setField(plan, "maxMessagesPerMonth", maxMessages);
        org.springframework.test.util.ReflectionTestUtils.setField(plan, "maxMembers", maxMembers);
        org.springframework.test.util.ReflectionTestUtils.setField(plan, "name", slug);
        org.springframework.test.util.ReflectionTestUtils.setField(plan, "priceUsdMonthly", BigDecimal.ZERO);
        org.springframework.test.util.ReflectionTestUtils.setField(plan, "isActive", true);
        return plan;
    }

    private MessageUsage messageUsageWith(int count) {
        MessageUsage usage = new MessageUsage();
        usage.setMsgCount(count);
        return usage;
    }
}
