package com.leonardtrinh.supportsaas.chat;

import java.time.Instant;
import java.util.UUID;

public record ConversationProjection(
    UUID id,
    UUID chatbotId,
    String sessionId,
    Instant createdAt,
    long messageCount,
    Instant lastMessageAt
) {}
