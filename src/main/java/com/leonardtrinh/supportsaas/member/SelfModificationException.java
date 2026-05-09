package com.leonardtrinh.supportsaas.member;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

public class SelfModificationException extends AppException {

    public SelfModificationException(String action) {
        super(HttpStatus.FORBIDDEN, "MEMBER_SELF_MODIFICATION",
              "Cannot perform action on yourself: " + action);
    }
}
