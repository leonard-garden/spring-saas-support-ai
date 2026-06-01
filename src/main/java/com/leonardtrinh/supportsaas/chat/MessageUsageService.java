package com.leonardtrinh.supportsaas.chat;

import java.util.UUID;

public interface MessageUsageService {

    /**
     * Checks if the tenant has exceeded their monthly message quota.
     * Throws QuotaExceededException (429) if limit is reached.
     */
    void checkQuota(UUID businessId, String yearMonth);

    /**
     * Atomically increments the message counter for the tenant/month.
     */
    void increment(UUID businessId, String yearMonth);
}
