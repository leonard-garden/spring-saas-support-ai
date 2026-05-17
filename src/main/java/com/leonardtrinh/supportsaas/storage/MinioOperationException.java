package com.leonardtrinh.supportsaas.storage;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

public class MinioOperationException extends AppException {
    public MinioOperationException(String message, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "MINIO_OPERATION_FAILED", message, cause);
    }
}
