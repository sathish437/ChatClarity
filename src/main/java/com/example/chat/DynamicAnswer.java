package com.example.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Dynamic structured-output contract for ChatGPT-style answers.
 *
 * <p>Unlike {@link AssistantAnswer}, which has a single fixed shape, this DTO lets
 * the model pick the fields that fit the user's intent. Only {@code answer} and
 * {@code language} are always populated; every other field is optional and may be
 * {@code null} when it does not apply (e.g. no code for a history question).
 *
 * <p>Compatible with {@code ChatClient ... .call().entity(DynamicAnswer.class)} on
 * the installed Spring AI version: plain records, lists and strings only, which
 * Jackson and {@code BeanOutputConverter} handle without extra configuration.
 */
@Schema(description = "Dynamic AI assistant answer with optional sections, examples, code and conclusion")
public record DynamicAnswer(
        @Schema(
                description = "Response style chosen by the model",
                example = "explanation",
                allowableValues = {"general", "explanation", "steps", "comparison", "code"})
        String responseType,

        @Schema(
                description = "Main assistant reply, written in the same language and style as the user (English, Tamil, Thanglish, or mixed)",
                example = "Total Quality Management focuses on continuous improvement.")
        String answer,

        @Schema(
                description = "Detected language/style of the user message",
                example = "English",
                allowableValues = {"English", "Tamil", "Thanglish", "Mixed"})
        String language,

        @Schema(description = "Optional bullet points summarising the key ideas; omit when not relevant")
        List<String> keyPoints,

        @Schema(description = "Optional titled sections for longer answers; omit when not relevant")
        List<AnswerSection> sections,

        @Schema(description = "Optional short illustrative examples; omit when not relevant")
        List<String> examples,

        @Schema(description = "Optional code snippets; omit when not relevant")
        List<CodeExample> codeExamples,

        @Schema(description = "Optional closing summary; omit when not relevant")
        String conclusion,

        @Schema(description = "Optional follow-up questions in the same language and style; omit when not relevant")
        List<String> followUpQuestions) {

    /** Optional titled block inside a longer answer. */
    @Schema(description = "A titled section of a longer answer")
    public record AnswerSection(
            @Schema(description = "Section heading", example = "Core Principles")
            String heading,
            @Schema(description = "Section body in the user's language and style")
            String body) {}

    /** Optional code snippet with its programming language. */
    @Schema(description = "A code snippet with its programming language")
    public record CodeExample(
            @Schema(description = "Programming language", example = "java")
            String language,
            @Schema(description = "Code content")
            String code) {}
}
