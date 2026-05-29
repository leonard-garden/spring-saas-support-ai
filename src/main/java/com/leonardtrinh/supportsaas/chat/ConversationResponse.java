package com.leonardtrinh.supportsaas.chat;

import java.time.Instant;
import java.util.UUID;

public record ConversationResponse(
    UUID id,
    UUID chatbotId,
    Instant createdAt
) {}
