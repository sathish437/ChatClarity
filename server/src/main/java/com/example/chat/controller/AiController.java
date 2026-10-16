package com.example.chat.controller;

import com.example.chat.dto.ChatRequest;
import com.example.chat.dto.DynamicAnswer;
import com.example.chat.service.ChatHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.google.genai.errors.ApiException;

@Tag(name = "AI Chat", description = "AI conversation API supporting session-based chat memory and multilingual interactions")
@RestController
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private final ChatClient chatClient;
    private final Validator validator;
    private final ChatHistoryService chatHistoryService;

    public AiController(ChatClient chatClient, Validator validator, ChatHistoryService chatHistoryService) {
        this.chatClient = chatClient;
        this.validator = validator;
        this.chatHistoryService = chatHistoryService;
    }

    @Operation(
            summary = "Send a message to the AI assistant",
            description = "Accepts a user message in the request body. Tracks conversation history per HTTP session and responds in the matching language and style (English, Tamil, Thanglish, or mixed). Returns a dynamic structured answer (DynamicAnswer) whose sections fit the question type.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Dynamic structured AI response",
                            content = @Content(schema = @Schema(implementation = DynamicAnswer.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid chat request"),
                    @ApiResponse(responseCode = "429", description = "AI service quota exhausted"),
                    @ApiResponse(responseCode = "502", description = "AI service returned an invalid or failed response"),
                    @ApiResponse(responseCode = "503", description = "AI service or network is unavailable"),
                    @ApiResponse(responseCode = "504", description = "AI service timed out")
            }
    )
    @PostMapping("/api/ai/ask")
    public DynamicAnswer ask(@Valid @RequestBody ChatRequest request, HttpSession session) {
        String conversationId = session.getId();

        try {
            DynamicAnswer structured = chatClient.prompt()
                    .user(request.message())
                    .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .entity(DynamicAnswer.class);

            if (structured == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI service returned an invalid response");
            }

            Set<ConstraintViolation<DynamicAnswer>> violations = validator.validate(structured);
            if (!violations.isEmpty()) {
                log.warn("AI structured response failed validation. violationCount={}", violations.size());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI service returned an invalid response");
            }

            persistHistory(conversationId, request.message(), structured);

            return structured;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception failure) {
            ApiException apiFailure = findCause(failure, ApiException.class);
            if (apiFailure != null) {
                if (apiFailure.code() == 429) {
                    log.warn("Gemini rate limit or quota exhausted (HTTP 429). No retry or fallback call made.");
                    throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                            "AI service rate limit or quota is exhausted. Please try again later.");
                }
                if (apiFailure.code() == 404) {
                    log.warn("Configured Gemini model or endpoint was not found (HTTP 404).");
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "Configured AI model or endpoint is unavailable.");
                }
                if (apiFailure.code() == 503) {
                    log.warn("Gemini provider remained unavailable after bounded retries (HTTP 503). No fallback call made.");
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "AI service temporarily unavailable. Please try again later.");
                }
                log.warn("Gemini provider request failed. providerStatus={}", apiFailure.code());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "AI service could not complete the request. Please try again later.");
            }
            if (findCause(failure, SocketTimeoutException.class) != null
                    || findCause(failure, HttpTimeoutException.class) != null
                    || findCause(failure, TimeoutException.class) != null) {
                log.warn("Gemini request timed out. No fallback call made.");
                throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                        "AI service timed out. Please try again later.");
            }
            if (findCause(failure, UnknownHostException.class) != null
                    || findCause(failure, ConnectException.class) != null
                    || findCause(failure, NoRouteToHostException.class) != null) {
                log.warn("Gemini service is unreachable. No fallback call made.");
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "AI service is unreachable. Please check network connectivity and try again.");
            }

            log.warn("AI response generation failed. failureType={}", failure.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "AI service could not generate a valid response. Please try again later.");
        }
    }

    private void persistHistory(String conversationId, String userMessage, DynamicAnswer answer) {
        try {
            chatHistoryService.recordExchange(conversationId, userMessage, answer);
        } catch (Exception historyFailure) {
            // History is a secondary concern: never fail the user's answer because of it.
            log.warn("Failed to persist chat history. failureType={}",
                    historyFailure.getClass().getSimpleName());
        }
    }

    private static <T extends Throwable> T findCause(Throwable failure, Class<T> type) {
        Throwable current = failure;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    /**
     * Renders controller errors with an explicit body so the reason (static,
     * key-free text) always reaches the client, independent of Boot's
     * {@code server.error.include-*} settings.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex,
            HttpServletRequest servletRequest) {
        int code = ex.getStatusCode().value();
        String phrase;
        try {
            phrase = HttpStatus.valueOf(code).getReasonPhrase();
        } catch (Exception ignored) {
            phrase = "Error";
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString());
        body.put("status", code);
        body.put("error", phrase);
        body.put("message", ex.getReason());
        body.put("path", servletRequest.getRequestURI());
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }
}
