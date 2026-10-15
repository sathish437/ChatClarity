package com.example.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;

/**
 * Focused tests for the {@link DynamicAnswer} DTO: Jackson compatibility with
 * partially populated payloads and {@code BeanOutputConverter} schema/conversion
 * behaviour on the installed Spring AI version.
 */
class DynamicAnswerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void partialJsonDeserializesWithNullOptionals() throws Exception {
        String json = """
                {"responseType":"general","answer":"Vanakkam!","language":"Tamil"}
                """;

        DynamicAnswer parsed = objectMapper.readValue(json, DynamicAnswer.class);

        assertThat(parsed.answer()).isEqualTo("Vanakkam!");
        assertThat(parsed.language()).isEqualTo("Tamil");
        assertThat(parsed.keyPoints()).isNull();
        assertThat(parsed.sections()).isNull();
        assertThat(parsed.examples()).isNull();
        assertThat(parsed.codeExamples()).isNull();
        assertThat(parsed.conclusion()).isNull();
        assertThat(parsed.followUpQuestions()).isNull();
    }

    @Test
    void fullJsonRoundTripPreservesAllFields() throws Exception {
        DynamicAnswer original = new DynamicAnswer(
                "code",
                "Here is a minimal REST controller.",
                "English",
                null,
                null,
                null,
                java.util.List.of(new DynamicAnswer.CodeExample("java", "record Hi() {}")),
                null,
                java.util.List.of("Want the POST variant?"));

        DynamicAnswer copy = objectMapper.readValue(objectMapper.writeValueAsString(original), DynamicAnswer.class);

        assertThat(copy).isEqualTo(original);
        assertThat(copy.codeExamples()).hasSize(1);
        assertThat(copy.codeExamples().get(0).language()).isEqualTo("java");
    }

    @Test
    void converterSchemaListsAllFields() {
        BeanOutputConverter<DynamicAnswer> converter = new BeanOutputConverter<>(DynamicAnswer.class);

        String format = converter.getFormat();
        assertThat(format).contains("responseType");
        assertThat(format).contains("answer");
        assertThat(format).contains("language");
        assertThat(format).contains("keyPoints");
        assertThat(format).contains("sections");
        assertThat(format).contains("examples");
        assertThat(format).contains("codeExamples");
        assertThat(format).contains("conclusion");
        assertThat(format).contains("followUpQuestions");
    }

    @Test
    void converterAcceptsPartialModelOutput() {
        BeanOutputConverter<DynamicAnswer> converter = new BeanOutputConverter<>(DynamicAnswer.class);

        String json = """
                {"responseType":"explanation","answer":"TQM means continuous improvement.","language":"English"}
                """;

        DynamicAnswer parsed = converter.convert(json);
        assertThat(parsed.answer()).isEqualTo("TQM means continuous improvement.");
        assertThat(parsed.language()).isEqualTo("English");
        assertThat(parsed.sections()).isNull();
        assertThat(parsed.codeExamples()).isNull();
    }

    @Test
    void converterAcceptsFullModelOutput() {
        BeanOutputConverter<DynamicAnswer> converter = new BeanOutputConverter<>(DynamicAnswer.class);

        String json = """
                {"responseType":"explanation","answer":"Intro.","language":"English",
                 "keyPoints":["Focus on the customer","Improve continuously"],
                 "sections":[{"heading":"Core Principles","body":"Details here."}],
                 "examples":["Pareto 80/20"],
                 "codeExamples":[],
                 "conclusion":"Start small.",
                 "followUpQuestions":["Want Deming's 14 points?"]}
                """;

        DynamicAnswer parsed = converter.convert(json);
        assertThat(parsed.responseType()).isEqualTo("explanation");
        assertThat(parsed.keyPoints()).containsExactly("Focus on the customer", "Improve continuously");
        assertThat(parsed.sections()).hasSize(1);
        assertThat(parsed.sections().get(0).heading()).isEqualTo("Core Principles");
        assertThat(parsed.conclusion()).isEqualTo("Start small.");
        assertThat(parsed.followUpQuestions()).containsExactly("Want Deming's 14 points?");
    }
}
