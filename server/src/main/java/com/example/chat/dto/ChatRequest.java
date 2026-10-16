package com.example.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request payload containing the user message")
public record ChatRequest(
        @NotBlank
        @Size(max = 12000)
        @Schema(description = "User message in English, Tamil, Thanglish, or mixed language", example = "My name is Sathish")
        String message
) {}
