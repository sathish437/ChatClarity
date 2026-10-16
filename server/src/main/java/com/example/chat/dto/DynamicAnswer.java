package com.example.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Structured response contract for the assistant. Optional content is represented
 * as empty immutable lists internally and omitted from JSON when not relevant.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Dynamic AI assistant answer with optional sections, examples, code and conclusion")
public record DynamicAnswer(
        @NotBlank
        @Pattern(regexp = "general|explanation|steps|comparison|code")
        @Schema(
                description = "Response style chosen by the model",
                example = "explanation",
                allowableValues = {"general", "explanation", "steps", "comparison", "code"})
        String responseType,

        @NotBlank
        @Size(max = 12000)
        @Schema(
                description = "Main assistant reply, written in the user's requested or detected language and style",
                example = "Total Quality Management focuses on continuous improvement.")
        String answer,

        @NotBlank
        @Pattern(regexp = "English|Tamil|Thanglish|Mixed")
        @Schema(
                description = "Detected language/style of the response",
                example = "English",
                allowableValues = {"English", "Tamil", "Thanglish", "Mixed"})
        String language,

        @Size(max = 12)
        @Schema(description = "Optional bullet points summarising key ideas")
        List<@NotBlank @Size(max = 1000) String> keyPoints,

        @Size(max = 16)
        @Schema(description = "Optional titled sections for longer answers")
        List<@NotNull @Valid AnswerSection> sections,

        @Size(max = 8)
        @Schema(description = "Optional short illustrative examples")
        List<@NotBlank @Size(max = 2000) String> examples,

        @Size(max = 5)
        @Schema(description = "Optional code snippets, only when relevant")
        List<@NotNull @Valid CodeExample> codeExamples,

        @Size(max = 3000)
        @Schema(description = "Optional closing summary")
        String conclusion,

        @Size(max = 5)
        @Schema(description = "Optional follow-up questions")
        List<@NotBlank @Size(max = 500) String> followUpQuestions) {

    public DynamicAnswer {
        keyPoints = distinct(keyPoints);
        sections = distinct(sections);
        examples = distinct(examples);
        codeExamples = distinct(codeExamples);
        followUpQuestions = distinct(followUpQuestions);
    }

    private static <T> List<T> distinct(List<T> values) {
        return values == null || values.isEmpty()
                ? List.of()
                : List.copyOf(new LinkedHashSet<>(values));
    }

    @Schema(description = "A titled section of a longer answer")
    public record AnswerSection(
            @NotBlank
            @Size(max = 200)
            @Schema(description = "Section heading", example = "Core Principles")
            String heading,

            @NotBlank
            @Size(max = 6000)
            @Schema(description = "Section body in the user's language and style")
            String body) {}

    @Schema(description = "A code snippet with its programming language")
    public record CodeExample(
            @NotBlank
            @Size(max = 50)
            @Schema(description = "Programming language", example = "java")
            String language,

            @NotBlank
            @Size(max = 12000)
            @Schema(description = "Code content")
            String code) {}
}
