package com.leonardtrinh.supportsaas.auth;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

public class EmailAlreadyExistsException extends AppException {

    public EmailAlreadyExistsException() {
        super(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "An account with this email already exists");
    }
}
