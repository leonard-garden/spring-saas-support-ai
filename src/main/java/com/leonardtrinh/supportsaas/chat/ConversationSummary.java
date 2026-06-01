package com.leonardtrinh.supportsaas.chat;

import java.time.Instant;
import java.util.UUID;

public record ConversationSummary(
    UUID id,
    UUID chatbotId,
    String chatbotName,
    String sessionId,
    long messageCount,
    Instant lastMessageAt,
    Instant createdAt
) {}
