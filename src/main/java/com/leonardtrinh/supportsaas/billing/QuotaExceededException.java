package com.leonardtrinh.supportsaas.billing;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

public class QuotaExceededException extends AppException {

    public QuotaExceededException(String resource, long limit) {
        super(HttpStatus.FORBIDDEN, "BILLING_QUOTA_EXCEEDED",
                "Quota exceeded for " + resource + ": limit=" + limit + ". Please upgrade your plan.");
    }
}
