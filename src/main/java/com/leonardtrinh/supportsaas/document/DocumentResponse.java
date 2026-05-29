package com.leonardtrinh.supportsaas.document;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        String filename,
        String contentType,
        DocumentStatus status,
        long sizeBytes,
        Integer chunkCount,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {}
