package com.leonardtrinh.supportsaas.auth;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

public class InvalidVerificationTokenException extends AppException {

    public InvalidVerificationTokenException(String message) {
        super(HttpStatus.BAD_REQUEST, "AUTH_INVALID_VERIFICATION_TOKEN", message);
    }
}
