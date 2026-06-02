package com.leonardtrinh.supportsaas.billing;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

public class SubscriptionNotCancellableException extends AppException {

    public SubscriptionNotCancellableException() {
        super(HttpStatus.BAD_REQUEST, "BILLING_NOT_CANCELLABLE",
                "Free plan has no subscription to cancel.");
    }
}
