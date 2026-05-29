package com.leonardtrinh.supportsaas.document;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

public class DocumentInProgressException extends AppException {
    public DocumentInProgressException() {
        super(HttpStatus.CONFLICT, "DOCUMENT_IN_PROGRESS", "Document is currently being processed and cannot be deleted");
    }
}
