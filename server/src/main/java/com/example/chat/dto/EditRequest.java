package com.example.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request payload to resend an edited user message, replacing the conversation branch after a given point")
public record EditRequest(
        @NotBlank
        @Size(max = 12000)
        @Schema(description = "Edited user message in English, Tamil, Thanglish, or mixed language", example = "My name is Sathish Kumar")
        String message,
        @Min(0)
        @Schema(description = "Number of completed exchanges to keep in memory before this message. Later exchanges are discarded.", example = "1")
        int keepExchanges
) {}
