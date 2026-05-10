package com.leonardtrinh.supportsaas.auth;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends AppException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid credentials");
    }
}
