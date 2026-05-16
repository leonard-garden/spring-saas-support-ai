package com.leonardtrinh.supportsaas.invitation;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class InvitationNotFoundException extends AppException {

    public InvitationNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND, "INVITATION_NOT_FOUND", "Invitation not found: id=" + id);
    }
}
