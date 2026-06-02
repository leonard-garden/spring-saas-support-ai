package com.leonardtrinh.supportsaas.chat;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

public class MessageTooLongException extends AppException {
    public MessageTooLongException(int maxLength) {
        super(HttpStatus.BAD_REQUEST, "MESSAGE_TOO_LONG",
                "Message exceeds maximum allowed length of " + maxLength + " characters");
    }
}
