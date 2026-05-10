package com.leonardtrinh.supportsaas.knowledgebase;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class KnowledgeBaseNotFoundException extends AppException {

    public KnowledgeBaseNotFoundException(UUID kbId) {
        super(HttpStatus.NOT_FOUND, "KB_NOT_FOUND", "Knowledge base not found: id=" + kbId);
    }
}
