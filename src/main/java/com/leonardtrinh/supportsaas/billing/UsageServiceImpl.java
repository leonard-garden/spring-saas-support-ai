package com.leonardtrinh.supportsaas.billing;

import com.leonardtrinh.supportsaas.chat.MessageUsageRepository;
import com.leonardtrinh.supportsaas.document.DocumentRepository;
import com.leonardtrinh.supportsaas.knowledgebase.KnowledgeBaseRepository;
import com.leonardtrinh.supportsaas.member.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UsageServiceImpl implements UsageService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentRepository documentRepository;
    private final MemberRepository memberRepository;
    private final MessageUsageRepository messageUsageRepository;

    public UsageServiceImpl(
            SubscriptionRepository subscriptionRepository,
            PlanRepository planRepository,
            KnowledgeBaseRepository knowledgeBaseRepository,
            DocumentRepository documentRepository,
            MemberRepository memberRepository,
            MessageUsageRepository messageUsageRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentRepository = documentRepository;
        this.memberRepository = memberRepository;
        this.messageUsageRepository = messageUsageRepository;
    }

    @Override
    public UsageResponse getUsage(UUID businessId) {
        Plan plan = resolvePlan(businessId);
        boolean unlimited = QuotaServiceImpl.PLAN_BUSINESS.equals(plan.getSlug());

        long kbUsed = knowledgeBaseRepository.countByBusinessId(businessId);
        long docsUsed = documentRepository.countByBusinessId(businessId);
        long messagesUsed = messageUsageRepository
                .findByBusinessIdAndYearMonth(businessId, YearMonth.now(ZoneOffset.UTC).toString())
                .map(u -> (long) u.getMsgCount())
                .orElse(0L);
        long membersUsed = memberRepository.countByBusinessId(businessId);

        long kbLimit = unlimited ? -1L : plan.getMaxKnowledgeBases();
        long docsLimit = unlimited ? -1L : plan.getMaxDocumentsPerKb();
        long messagesLimit = plan.getMaxMessagesPerMonth();
        long membersLimit = unlimited ? -1L : plan.getMaxMembers();

        return new UsageResponse(
                new UsageMetric(kbUsed, kbLimit),
                new UsageMetric(docsUsed, docsLimit),
                new UsageMetric(messagesUsed, messagesLimit),
                new UsageMetric(membersUsed, membersLimit));
    }

    private Plan resolvePlan(UUID businessId) {
        return subscriptionRepository.findActiveByBusinessId(businessId)
                .map(sub -> planRepository.findById(sub.getPlanId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Plan not found for subscription: " + sub.getId())))
                .orElseGet(() -> planRepository.findBySlug("free")
                        .orElseThrow(() -> new IllegalStateException("Free plan not found")));
    }
}
