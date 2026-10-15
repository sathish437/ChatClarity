package com.example.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Structured LLM output contract for the AI assistant.
 *
 * <p>Used with {@code ChatClient ... .call().entity(AssistantAnswer.class)} so the
 * Gemini model returns typed JSON instead of free-form text. Spring AI's
 * {@code BeanOutputConverter} injects this schema into the prompt and converts the
 * model reply into this record.
 */
@Schema(description = "Structured AI assistant answer produced via Spring AI structured output")
public record AssistantAnswer(
        @Schema(
                description = "Main assistant reply, written in the same language and style as the user (English, Tamil, Thanglish, or mixed)",
                example = "Nice to meet you, Sathish. How can I help you today?")
        String answer,

        @Schema(
                description = "Detected language/style of the user message",
                example = "English",
                allowableValues = {"English", "Tamil", "Thanglish", "Mixed"})
        String language,

        @Schema(
                description = "Up to 3 helpful follow-up questions in the same language and style",
                example = "[\"What would you like to learn first?\"]")
        List<String> followUpQuestions) {}
