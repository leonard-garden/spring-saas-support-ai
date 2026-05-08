package com.leonardtrinh.supportsaas.auth;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

public class PlanMisconfiguredException extends AppException {
    public PlanMisconfiguredException(String planSlug) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "PLAN_MISCONFIGURED",
              "Required plan '" + planSlug + "' not found. Check seed data.");
    }
}
