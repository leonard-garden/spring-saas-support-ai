package com.leonardtrinh.supportsaas.auth;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

public class BusinessSuspendedException extends AppException {

    public BusinessSuspendedException() {
        super(HttpStatus.FORBIDDEN, "BUSINESS_SUSPENDED", "This account has been suspended");
    }
}
