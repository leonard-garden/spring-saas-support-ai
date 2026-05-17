package com.leonardtrinh.supportsaas.storage;

public class MinioOperationException extends RuntimeException {
    public MinioOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
