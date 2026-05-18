package com.leonardtrinh.supportsaas.document;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

public class FileValidationException extends AppException {
    public FileValidationException(String message) {
        super(HttpStatus.BAD_REQUEST, "FILE_VALIDATION_FAILED", message);
    }
}
