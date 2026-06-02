package com.leonardtrinh.supportsaas.billing;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

public class StripeGatewayException extends AppException {

    public StripeGatewayException(String message, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, "STRIPE_GATEWAY_ERROR", message, cause);
    }
}
