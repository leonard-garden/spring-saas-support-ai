package com.leonardtrinh.supportsaas.billing;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

public class CannotUpgradeException extends AppException {

    public CannotUpgradeException(String reason) {
        super(HttpStatus.BAD_REQUEST, "BILLING_CANNOT_UPGRADE", reason);
    }
}
