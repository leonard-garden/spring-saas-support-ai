package com.leonardtrinh.supportsaas.chat;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class ConversationNotFoundException extends AppException {

    public ConversationNotFoundException(UUID conversationId) {
        super(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND",
                "Conversation not found: id=" + conversationId);
    }
}
