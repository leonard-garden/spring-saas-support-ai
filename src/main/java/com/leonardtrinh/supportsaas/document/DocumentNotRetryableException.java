package com.leonardtrinh.supportsaas.document;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

public class DocumentNotRetryableException extends AppException {
    public DocumentNotRetryableException() {
        super(HttpStatus.CONFLICT, "DOCUMENT_NOT_RETRYABLE",
                "Only FAILED documents can be retried");
    }
}
