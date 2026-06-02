package com.leonardtrinh.supportsaas.billing;

import java.util.UUID;

/**
 * Enforces per-tenant plan quotas with a 10% grace period for paid (ACTIVE/TRIALING) tenants.
 *
 * <p>Grace period rules:
 * <ul>
 *   <li>Paid tenants (ACTIVE, TRIALING): effectiveLimit = floor(max * 1.1)</li>
 *   <li>Free / PAST_DUE / CANCELED: effectiveLimit = max (hard limit)</li>
 *   <li>Business plan (slug = "business"): unlimited — all checks short-circuit to allow</li>
 * </ul>
 *
 * <p>Throws {@link QuotaExceededException} when {@code current >= effectiveLimit}.
 */
public interface QuotaService {

    /**
     * Checks whether the tenant can create another knowledge base.
     *
     * @param businessId  the tenant's business UUID
     * @param currentCount the number of knowledge bases already owned
     * @throws QuotaExceededException if the limit is reached
     */
    void checkKnowledgeBaseQuota(UUID businessId, long currentCount);

    /**
     * Checks whether the tenant can upload another document to a knowledge base.
     *
     * @param businessId  the tenant's business UUID
     * @param currentCount the number of documents already in the knowledge base
     * @throws QuotaExceededException if the limit is reached
     */
    void checkDocumentQuota(UUID businessId, long currentCount);

    /**
     * Checks whether the tenant can send another chat message this month.
     *
     * @param businessId  the tenant's business UUID
     * @param currentCount the number of messages sent so far this month
     * @throws QuotaExceededException if the limit is reached
     */
    void checkMessageQuota(UUID businessId, long currentCount);
}
