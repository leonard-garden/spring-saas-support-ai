package com.leonardtrinh.supportsaas.auth;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

public class InvalidResetTokenException extends AppException {

    public InvalidResetTokenException(String message) {
        super(HttpStatus.BAD_REQUEST, "AUTH_INVALID_RESET_TOKEN", message);
    }
}
