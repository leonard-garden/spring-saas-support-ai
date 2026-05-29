package com.leonardtrinh.supportsaas.chatbot;

import java.util.UUID;

public record EmbedResponse(
    String snippet,
    String widgetUrl,
    UUID chatbotId
) {}
