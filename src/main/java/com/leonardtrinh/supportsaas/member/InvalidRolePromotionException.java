package com.leonardtrinh.supportsaas.member;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

public class InvalidRolePromotionException extends AppException {

    public InvalidRolePromotionException() {
        super(HttpStatus.BAD_REQUEST, "MEMBER_INVALID_ROLE_PROMOTION",
              "Cannot promote a member to OWNER role.");
    }
}
