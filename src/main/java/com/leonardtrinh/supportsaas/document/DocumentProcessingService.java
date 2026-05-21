package com.leonardtrinh.supportsaas.document;

import java.util.UUID;

public interface DocumentProcessingService {
    void processAsync(UUID documentId, UUID businessId);
}
