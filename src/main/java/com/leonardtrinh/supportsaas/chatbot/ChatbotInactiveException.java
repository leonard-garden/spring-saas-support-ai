package com.leonardtrinh.supportsaas.chatbot;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;
import java.util.UUID;

public class ChatbotInactiveException extends AppException {
    public ChatbotInactiveException(UUID chatbotId) {
        super(HttpStatus.FORBIDDEN, "CHATBOT_INACTIVE",
                "Chatbot is inactive: id=" + chatbotId);
    }
}
