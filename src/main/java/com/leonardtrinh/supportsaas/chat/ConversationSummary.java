package com.leonardtrinh.supportsaas.chat;

import java.time.Instant;
import java.util.UUID;

public record ConversationSummary(
    UUID id,
    UUID chatbotId,
    String chatbotName,
    int messageCount,
    Instant lastMessageAt,
    Instant createdAt
) {}
