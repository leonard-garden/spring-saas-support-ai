package com.leonardtrinh.supportsaas.member;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class MemberNotFoundException extends AppException {

    public MemberNotFoundException(UUID memberId) {
        super(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "Member not found: id=" + memberId);
    }

    public MemberNotFoundException(String email) {
        super(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "Member not found: email=" + email);
    }
}
