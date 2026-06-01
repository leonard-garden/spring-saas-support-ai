package com.leonardtrinh.supportsaas.chatbot;

import java.time.Instant;
import java.util.UUID;

public record ChatbotResponse(
    UUID id,
    String name,
    String welcomeMessage,
    String primaryColor,
    UUID kbId,
    boolean isActive,
    String embedSnippet,
    Instant createdAt
) {}
