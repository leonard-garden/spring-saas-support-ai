package com.leonardtrinh.supportsaas.knowledgebase;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeBaseResponse(
        UUID id,
        UUID businessId,
        long documentCount,
        long readyCount,
        Instant createdAt
) {}
