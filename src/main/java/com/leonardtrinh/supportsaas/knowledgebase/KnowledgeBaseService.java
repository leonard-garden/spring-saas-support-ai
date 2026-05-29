package com.leonardtrinh.supportsaas.knowledgebase;

import java.util.UUID;

public interface KnowledgeBaseService {
    KnowledgeBaseResponse getForCurrentTenant();
    KnowledgeBase createForBusiness(UUID businessId);
}
