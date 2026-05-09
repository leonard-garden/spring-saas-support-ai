package com.leonardtrinh.supportsaas.member;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

public class InvitationExpiredException extends AppException {

    public InvitationExpiredException(String token) {
        super(HttpStatus.GONE, "MEMBER_INVITATION_EXPIRED", "Invitation has expired or already used: token=" + token);
    }
}
