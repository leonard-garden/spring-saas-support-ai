package com.leonardtrinh.supportsaas.document.search;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

public class NoReadyDocumentsException extends AppException {
    public NoReadyDocumentsException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "NO_READY_DOCUMENTS",
                "No ready documents found. Please upload and process documents first.");
    }
}
