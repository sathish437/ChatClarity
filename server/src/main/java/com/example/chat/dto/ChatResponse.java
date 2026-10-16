package com.example.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response payload containing the AI response")
public record ChatResponse(
        @Schema(description = "AI response text adhering to the user's language and style", example = "Nice to meet you, Sathish.")
        String response
) {}
