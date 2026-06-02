package com.leonardtrinh.supportsaas.billing;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

public class AlreadyCancelledAtPeriodEndException extends AppException {

    public AlreadyCancelledAtPeriodEndException() {
        super(HttpStatus.BAD_REQUEST, "BILLING_ALREADY_CANCELLED",
                "Subscription is already set to cancel at period end.");
    }
}
