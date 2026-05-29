package com.leonardtrinh.supportsaas.knowledgebase;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

public class KnowledgeBaseNotFoundException extends AppException {
    public KnowledgeBaseNotFoundException() {
        super(HttpStatus.NOT_FOUND, "KB_NOT_FOUND", "Knowledge base not found");
    }
}
