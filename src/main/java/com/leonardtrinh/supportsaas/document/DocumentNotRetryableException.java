package com.leonardtrinh.supportsaas.document;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class DocumentNotRetryableException extends AppException {
    public DocumentNotRetryableException(UUID id, DocumentStatus currentStatus) {
        super(HttpStatus.CONFLICT, "DOCUMENT_NOT_RETRYABLE",
                "Document " + id + " cannot be retried (current status: " + currentStatus + "). Only FAILED documents can be retried.");
    }
}
