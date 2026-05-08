package com.leonardtrinh.supportsaas.common;

import org.springframework.http.HttpStatus;

import java.net.URI;

public abstract class AppException extends RuntimeException {

    private static final String PROBLEM_BASE_URI = "https://problems.supportsaas.io/";

    private final HttpStatus status;
    private final String errorCode;

    protected AppException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    protected AppException(HttpStatus status, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    // Derives URI slug from class name: ExpiredTokenException → expired-token
    public URI getProblemType() {
        String slug = getClass().getSimpleName()
                .replaceAll("([a-z])([A-Z])", "$1-$2")
                .toLowerCase()
                .replace("-exception", "");
        return URI.create(PROBLEM_BASE_URI + slug);
    }
}
