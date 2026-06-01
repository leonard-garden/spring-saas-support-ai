package com.leonardtrinh.supportsaas.chat;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageResponse(
    UUID id,
    MessageRole role,
    String content,
    Instant createdAt
) {}
