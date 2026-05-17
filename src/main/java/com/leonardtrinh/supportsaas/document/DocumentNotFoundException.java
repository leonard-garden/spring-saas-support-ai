package com.leonardtrinh.supportsaas.document;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class DocumentNotFoundException extends AppException {
    public DocumentNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Document not found: " + id);
    }
}
