package com.leonardtrinh.supportsaas.auth;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

public class InvalidTokenException extends AppException {

    public InvalidTokenException(String message) {
        super(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_TOKEN", message);
    }
}
