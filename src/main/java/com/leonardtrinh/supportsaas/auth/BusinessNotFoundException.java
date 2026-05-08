package com.leonardtrinh.supportsaas.auth;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class BusinessNotFoundException extends AppException {
    public BusinessNotFoundException(UUID businessId) {
        super(HttpStatus.NOT_FOUND, "BUSINESS_NOT_FOUND",
              "Business not found: " + businessId);
    }
}
