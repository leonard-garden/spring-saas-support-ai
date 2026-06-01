package com.leonardtrinh.supportsaas.chatbot;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ReplaceKnowledgeBasesRequest(
    @NotNull(message = "kbIds must not be null")
    List<UUID> kbIds
) {}
