package com.leonardtrinh.supportsaas.chatbot;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class ChatbotNotFoundException extends AppException {

    public ChatbotNotFoundException(UUID chatbotId) {
        super(HttpStatus.NOT_FOUND, "CHATBOT_NOT_FOUND",
                "Chatbot not found: id=" + chatbotId);
    }

    public ChatbotNotFoundException() {
        super(HttpStatus.NOT_FOUND, "CHATBOT_NOT_FOUND", "Chatbot not found or inactive");
    }
}
