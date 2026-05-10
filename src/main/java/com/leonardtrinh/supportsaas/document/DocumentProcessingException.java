package com.leonardtrinh.supportsaas.document;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class DocumentProcessingException extends AppException {

    public DocumentProcessingException(UUID documentId, String reason) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "DOCUMENT_PROCESSING_FAILED",
                "Failed to process document: id=" + documentId + ", reason=" + reason);
    }

    public DocumentProcessingException(UUID documentId, String reason, Throwable cause) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "DOCUMENT_PROCESSING_FAILED",
                "Failed to process document: id=" + documentId + ", reason=" + reason, cause);
    }
}
