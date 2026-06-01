package com.leonardtrinh.supportsaas.chatbot;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WidgetResponse(
    UUID id,
    String name,
    String welcomeMessage,
    String primaryColor,
    boolean isActive,
    List<UUID> kbIds,
    String embedSnippet,
    Instant createdAt
) {}
