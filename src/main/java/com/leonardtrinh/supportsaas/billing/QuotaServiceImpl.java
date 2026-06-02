package com.leonardtrinh.supportsaas.billing;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Enforces plan quotas with optional 10% grace period for paid tenants.
 *
 * <p>Logic summary:
 * <ol>
 *   <li>Resolve active subscription → plan for the tenant.</li>
 *   <li>If plan slug is "business" → unlimited, short-circuit allow.</li>
 *   <li>isPaid = subscription status IN (ACTIVE, TRIALING).</li>
 *   <li>effectiveLimit = isPaid ? floor(max * 1.1) : max.</li>
 *   <li>Throw {@link QuotaExceededException} when current >= effectiveLimit.</li>
 * </ol>
 *
 * <p>If no active subscription is found for a tenant, falls back to the "free" plan limits
 * with no grace period.
 */
@Service
@Transactional(readOnly = true)
public class QuotaServiceImpl implements QuotaService {

    static final String PLAN_BUSINESS = "business";
    static final double GRACE_MULTIPLIER = 1.1;

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;

    public QuotaServiceImpl(SubscriptionRepository subscriptionRepository,
                            PlanRepository planRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
    }

    @Override
    public void checkKnowledgeBaseQuota(UUID businessId, long currentCount) {
        PlanContext ctx = resolvePlanContext(businessId);
        if (ctx.unlimited()) return;
        long effective = effectiveLimit(ctx.plan().getMaxKnowledgeBases(), ctx.isPaid());
        if (currentCount >= effective) {
            throw new QuotaExceededException("knowledge_bases", effective, currentCount);
        }
    }

    @Override
    public void checkDocumentQuota(UUID businessId, long currentCount) {
        PlanContext ctx = resolvePlanContext(businessId);
        if (ctx.unlimited()) return;
        long effective = effectiveLimit(ctx.plan().getMaxDocumentsPerKb(), ctx.isPaid());
        if (currentCount >= effective) {
            throw new QuotaExceededException("documents_per_kb", effective, currentCount);
        }
    }

    @Override
    public void checkMessageQuota(UUID businessId, long currentCount) {
        PlanContext ctx = resolvePlanContext(businessId);
        if (ctx.unlimited()) return;
        long effective = effectiveLimit(ctx.plan().getMaxMessagesPerMonth(), ctx.isPaid());
        if (currentCount >= effective) {
            throw new QuotaExceededException("messages_per_month", effective, currentCount);
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves the current plan and paid status for a tenant.
     * Falls back to the free plan (no grace period) when no active subscription exists.
     */
    private PlanContext resolvePlanContext(UUID businessId) {
        return subscriptionRepository.findActiveByBusinessId(businessId)
                .map(sub -> {
                    Plan plan = planRepository.findById(sub.getPlanId())
                            .orElseThrow(() -> new IllegalStateException(
                                    "Plan not found for subscription: " + sub.getId()));
                    boolean unlimited = PLAN_BUSINESS.equals(plan.getSlug());
                    boolean isPaid = isPaidStatus(sub.getStatus());
                    return new PlanContext(plan, isPaid, unlimited);
                })
                .orElseGet(() -> {
                    // No active subscription — resolve free plan as fallback
                    Plan free = planRepository.findBySlug("free")
                            .orElseThrow(() -> new IllegalStateException("Free plan not found"));
                    return new PlanContext(free, false, false);
                });
    }

    /**
     * Returns true when the subscription status qualifies for the grace period.
     * Only ACTIVE and TRIALING subscriptions are considered paid.
     */
    private boolean isPaidStatus(SubscriptionStatus status) {
        return status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.TRIALING;
    }

    /**
     * Calculates the effective quota limit.
     * Paid tenants get a 10% grace period: effectiveLimit = floor(max * 1.1).
     * Free / PAST_DUE / CANCELED tenants get the hard plan limit.
     */
    private long effectiveLimit(int planMax, boolean isPaid) {
        if (isPaid) {
            return (long) Math.floor(planMax * GRACE_MULTIPLIER);
        }
        return planMax;
    }

    /**
     * Internal value type carrying resolved plan context for a single quota check.
     */
    private record PlanContext(Plan plan, boolean isPaid, boolean unlimited) {}
}
