package com.example.chat;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request payload containing the user message")
public record ChatRequest(
        @Schema(description = "User message in English, Tamil, Thanglish, or mixed language", example = "My name is Sathish")
        String message
) {}
