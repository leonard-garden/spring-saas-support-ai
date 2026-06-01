package com.leonardtrinh.supportsaas.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
    @NotBlank(message = "Query must not be blank")
    @Size(max = 2000, message = "Query must not exceed 2000 characters")
    String query
) {}
