package com.leonardtrinh.supportsaas.billing;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

public class AlreadySubscribedException extends AppException {

    public AlreadySubscribedException() {
        super(HttpStatus.BAD_REQUEST, "BILLING_ALREADY_SUBSCRIBED",
                "Tenant already has an active subscription.");
    }
}
