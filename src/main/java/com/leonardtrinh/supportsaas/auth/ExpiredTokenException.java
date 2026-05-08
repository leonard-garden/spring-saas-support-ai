package com.leonardtrinh.supportsaas.auth;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

public class ExpiredTokenException extends AppException {

    public ExpiredTokenException(String message) {
        super(HttpStatus.UNAUTHORIZED, "AUTH_EXPIRED_TOKEN", message);
    }
}
