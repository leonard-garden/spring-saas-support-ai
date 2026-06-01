package com.leonardtrinh.supportsaas.chat;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateConversationRequest(
    @NotNull(message = "chatbotId must not be null")
    UUID chatbotId
) {}
