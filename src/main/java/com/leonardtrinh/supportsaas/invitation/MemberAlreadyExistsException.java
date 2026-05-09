package com.leonardtrinh.supportsaas.invitation;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

public class MemberAlreadyExistsException extends AppException {

    public MemberAlreadyExistsException(String email) {
        super(HttpStatus.CONFLICT, "MEMBER_ALREADY_EXISTS",
              "Email is already a member or has a pending invitation: " + email);
    }
}
