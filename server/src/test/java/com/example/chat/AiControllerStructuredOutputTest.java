package com.example.chat;

import com.example.chat.controller.AiController;
import com.example.chat.dto.DynamicAnswer;
import com.example.chat.service.ChatBranchService;
import com.example.chat.service.ChatHistoryService;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
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

    @MockitoBean
    private ChatHistoryService chatHistoryService;

    @MockitoBean
    private ChatBranchService chatBranchService;

    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callResponseSpec;

    @BeforeEach
    void stubChatClientChain() {
        requestSpec = org.mockito.Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        callResponseSpec = org.mockito.Mockito.mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.advisors(org.mockito.ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any()))
                .thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
    }

    @Test
    void responseHasStructuredFields() throws Exception {
        DynamicAnswer stubbed = new DynamicAnswer(
                "general",
                "Nice to meet you, Sathish.",
                "English",
                null, null, null, null, null,
                List.of("What would you like to learn first?"));
        when(callResponseSpec.entity(eq(DynamicAnswer.class))).thenReturn(stubbed);

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"My name is Sathish\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseType").value("general"))
                .andExpect(jsonPath("$.answer").value("Nice to meet you, Sathish."))
                .andExpect(jsonPath("$.language").value("English"))
                .andExpect(jsonPath("$.followUpQuestions[0]").value("What would you like to learn first?"));
    }

    @Test
    void quotaExceptionReturns429WithoutFallbackCall() throws Exception {
        RuntimeException quotaFailure = new RuntimeException("Failed to generate content",
                new ClientException(429, "quota exceeded", "RESOURCE_EXHAUSTED"));
        when(callResponseSpec.entity(eq(DynamicAnswer.class))).thenThrow(quotaFailure);

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message")
                        .value("AI service returned a rate limit response. Please try again later."));

        verify(chatClient, times(1)).prompt();
        verify(callResponseSpec, never()).content();
    }

    @Test
    void transientRateLimitReturnsHighDemandMessage() throws Exception {
        String providerDetail = "Quota exceeded for metric. Details: "
                + "{\"@type\":\"type.googleapis.com/google.rpc.RetryInfo\",\"retryDelay\":\"5s\"}";
        RuntimeException transientFailure = new RuntimeException("Failed to generate content",
                new ClientException(429, "RESOURCE_EXHAUSTED", providerDetail));
        when(callResponseSpec.entity(eq(DynamicAnswer.class))).thenThrow(transientFailure);

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message")
                        .value("The AI service is handling high demand right now. Please wait a moment and try again."));

        verify(chatClient, times(1)).prompt();
        verify(callResponseSpec, never()).content();
    }

    @Test
    void dailyQuotaWithRetryDelayDoesNotReturnTransientHighDemandMessage() throws Exception {
        String providerDetail = "Daily request limit exceeded. Details: "
                + "[{\"@type\":\"type.googleapis.com/google.rpc.QuotaFailure\","
                + "\"violations\":[{\"subject\":\"GenerateRequestsPerDay\"}]},"
                + "{\"@type\":\"type.googleapis.com/google.rpc.RetryInfo\",\"retryDelay\":\"5s\"}]";
        RuntimeException dailyQuotaFailure = new RuntimeException("Failed to generate content",
                new ClientException(429, "RESOURCE_EXHAUSTED", providerDetail));
        when(callResponseSpec.entity(eq(DynamicAnswer.class))).thenThrow(dailyQuotaFailure);

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message")
                        .value("AI service returned a rate limit response. Please try again later."));

        verify(chatClient, times(1)).prompt();
        verify(callResponseSpec, never()).content();
    }

    @Test
    void providerUnavailableReturns503WithoutFallbackCall() throws Exception {
        RuntimeException unavailable = new RuntimeException("Failed to generate content",
                new ServerException(503, "high demand", "UNAVAILABLE"));
        when(callResponseSpec.entity(eq(DynamicAnswer.class))).thenThrow(unavailable);

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isServiceUnavailable());

        verify(callResponseSpec, never()).content();
    }

    @Test
    void missingModelReturnsSanitized502WithoutFallbackCall() throws Exception {
        RuntimeException missingModel = new RuntimeException("Failed to generate content",
                new ClientException(404, "Not Found", "model resource was not found"));
        when(callResponseSpec.entity(eq(DynamicAnswer.class))).thenThrow(missingModel);

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Configured AI model or endpoint is unavailable."));

        verify(chatClient, times(1)).prompt();
        verify(callResponseSpec, never()).content();
    }

    @Test
    void providerErrorReturnsSanitized502WithoutFallbackCall() throws Exception {
        String secretMarker = "PROVIDER_SECRET_SHOULD_NOT_LEAK";
        RuntimeException providerFailure = new RuntimeException("provider failure " + secretMarker,
                new ClientException(401, "provider detail " + secretMarker, "UNAUTHENTICATED"));
        when(callResponseSpec.entity(eq(DynamicAnswer.class))).thenThrow(providerFailure);

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message")
                        .value("AI service could not complete the request. Please try again later."))
                .andExpect(content().string(not(containsString(secretMarker))))
                .andExpect(content().string(not(containsString("RuntimeException"))));

        verify(chatClient, times(1)).prompt();
        verify(callResponseSpec, never()).content();
    }

    @Test
    void modelFailureReturnsSanitized502WithoutFallbackCall() throws Exception {
        String secretMarker = "TEST_API_KEY_SHOULD_NOT_LEAK";
        when(callResponseSpec.entity(eq(DynamicAnswer.class)))
                .thenThrow(new RuntimeException("gemini down " + secretMarker + "\n at com.example.SecretProvider.call"));

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message")
                        .value("AI service could not generate a valid response. Please try again later."))
                .andExpect(content().string(not(containsString(secretMarker))))
                .andExpect(content().string(not(containsString("SecretProvider"))))
                .andExpect(content().string(not(containsString("RuntimeException"))));

        verify(chatClient, times(1)).prompt();
        verify(callResponseSpec, never()).content();
    }

    @Test
    void malformedStructuredOutputReturnsSanitizedErrorWithoutRetry() throws Exception {
        when(callResponseSpec.entity(eq(DynamicAnswer.class)))
                .thenThrow(new IllegalArgumentException("malformed JSON with SECRET_MARKER"));

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(content().string(not(containsString("SECRET_MARKER"))));

        verify(chatClient, times(1)).prompt();
        verify(callResponseSpec, never()).content();
    }

    @Test
    void invalidStructuredFieldsReturnSanitizedErrorWithoutRetry() throws Exception {
        when(callResponseSpec.entity(eq(DynamicAnswer.class))).thenReturn(
                new DynamicAnswer("unknown", " ", "English", null, null, null, null, null, null));

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("AI service returned an invalid response"));

        verify(callResponseSpec, never()).content();
    }

    @Test
    void dnsFailureReturnsServiceUnavailableWithoutRetry() throws Exception {
        when(callResponseSpec.entity(eq(DynamicAnswer.class)))
                .thenThrow(new RuntimeException("provider connection failed",
                        new UnknownHostException("SECRET_INTERNAL_HOST")));

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message")
                        .value("AI service is unreachable. Please check network connectivity and try again."))
                .andExpect(content().string(not(containsString("SECRET_INTERNAL_HOST"))));

        verify(chatClient, times(1)).prompt();
        verify(callResponseSpec, never()).content();
    }

    @Test
    void modelTimeoutReturnsGatewayTimeoutWithoutRetry() throws Exception {
        when(callResponseSpec.entity(eq(DynamicAnswer.class)))
                .thenThrow(new RuntimeException("provider timeout", new SocketTimeoutException("timed out")));

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.message").value("AI service timed out. Please try again later."));

        verify(chatClient, times(1)).prompt();
        verify(callResponseSpec, never()).content();
    }

    @Test
    void providerFailureDoesNotReturnSecretOrTrace() throws Exception {
        String secretMarker = "TEST_API_KEY_SHOULD_NOT_LEAK";
        when(callResponseSpec.entity(eq(DynamicAnswer.class)))
                .thenThrow(new RuntimeException("provider failed " + secretMarker));

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(content().string(not(containsString(secretMarker))))
                .andExpect(content().string(not(containsString("RuntimeException"))));
    }

    @Test
    void blankUserMessageIsRejectedBeforeModelCall() throws Exception {
        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"  \"}"))
                .andExpect(status().isBadRequest());

        verify(chatClient, never()).prompt();
    }

}
