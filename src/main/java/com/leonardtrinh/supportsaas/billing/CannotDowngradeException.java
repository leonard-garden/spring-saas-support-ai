package com.leonardtrinh.supportsaas.billing;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

public class CannotDowngradeException extends AppException {

    public CannotDowngradeException(String reason) {
        super(HttpStatus.BAD_REQUEST, "BILLING_CANNOT_DOWNGRADE", reason);
    }
}
