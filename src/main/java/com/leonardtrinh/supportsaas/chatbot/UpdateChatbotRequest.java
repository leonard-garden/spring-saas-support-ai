package com.leonardtrinh.supportsaas.chatbot;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateChatbotRequest(
    @Size(max = 100, message = "Name must not exceed 100 characters")
    String name,

    @Size(max = 500, message = "Welcome message must not exceed 500 characters")
    String welcomeMessage,

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Primary color must be a valid hex color e.g. #3B82F6")
    String primaryColor,

    UUID kbId,

    Boolean isActive
) {}
