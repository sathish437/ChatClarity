package com.example.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.google.genai.errors.ClientException;
import com.google.genai.errors.ServerException;

@WebMvcTest(AiController.class)
class AiControllerStructuredOutputTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatClient chatClient;

    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callResponseSpec;

    @BeforeEach
    void stubChatClientChain() {
        requestSpec = org.mockito.Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        callResponseSpec = org.mockito.Mockito.mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.advisors(any(Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
    }

    @Test
    void responseHasStructuredFields() throws Exception {
        AssistantAnswer stubbed = new AssistantAnswer(
                "Nice to meet you, Sathish.",
                "English",
                List.of("What would you like to learn first?"));
        when(callResponseSpec.entity(eq(AssistantAnswer.class))).thenReturn(stubbed);

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"My name is Sathish\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Nice to meet you, Sathish."))
                .andExpect(jsonPath("$.language").value("English"))
                .andExpect(jsonPath("$.followUpQuestions[0]").value("What would you like to learn first?"));
    }

    @Test
    void quotaExceptionReturns429WithoutFallbackCall() throws Exception {
        RuntimeException quotaFailure = new RuntimeException("Failed to generate content",
                new ClientException(429, "quota exceeded", "RESOURCE_EXHAUSTED"));
        when(callResponseSpec.entity(eq(AssistantAnswer.class))).thenThrow(quotaFailure);

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("AI service quota exhausted. Please try again later."));

        verify(callResponseSpec, never()).content();
    }

    @Test
    void providerUnavailableReturns503WithoutFallbackCall() throws Exception {
        RuntimeException unavailable = new RuntimeException("Failed to generate content",
                new ServerException(503, "high demand", "UNAVAILABLE"));
        when(callResponseSpec.entity(eq(AssistantAnswer.class))).thenThrow(unavailable);

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isServiceUnavailable());

        verify(callResponseSpec, never()).content();
    }
    @Test
    void modelFailureReturns500() throws Exception {
        when(callResponseSpec.entity(eq(AssistantAnswer.class)))
                .thenThrow(new RuntimeException("gemini down"));
        when(callResponseSpec.content()).thenThrow(new RuntimeException("gemini down"));

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void structuredOutputFailureFallsBackToPlainText() throws Exception {
        when(callResponseSpec.entity(eq(AssistantAnswer.class)))
                .thenThrow(new RuntimeException("conversion failed"));
        when(callResponseSpec.content()).thenReturn("Plain fallback reply");

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Plain fallback reply"))
                .andExpect(jsonPath("$.language").value("Mixed"));
    }

    @Test
    void structuredOutputConverterSchemaAndParsing() {
        BeanOutputConverter<AssistantAnswer> converter = new BeanOutputConverter<>(AssistantAnswer.class);

        String format = converter.getFormat();
        assertThat(format).contains("answer");
        assertThat(format).contains("language");
        assertThat(format).contains("followUpQuestions");

        String json = """
                {"answer":"Vanakkam!","language":"Tamil","followUpQuestions":["Ungalukku enna vendum?"]}
                """;
        AssistantAnswer parsed = converter.convert(json);
        assertThat(parsed.answer()).isEqualTo("Vanakkam!");
        assertThat(parsed.language()).isEqualTo("Tamil");
        assertThat(parsed.followUpQuestions()).containsExactly("Ungalukku enna vendum?");
    }
}
