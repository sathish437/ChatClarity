package com.example.chat.controller;

import com.example.chat.dto.ChatRequest;
import com.example.chat.dto.DynamicAnswer;
import com.example.chat.dto.EditRequest;
import com.example.chat.service.ChatBranchService;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
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
    private final ChatBranchService chatBranchService;

    public AiController(ChatClient chatClient, Validator validator, ChatHistoryService chatHistoryService,
            ChatBranchService chatBranchService) {
        this.chatClient = chatClient;
        this.validator = validator;
        this.chatHistoryService = chatHistoryService;
        this.chatBranchService = chatBranchService;
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
        long startTime = System.nanoTime();

        try {
            DynamicAnswer structured = generateAnswer(request.message(), conversationId);
            persistHistory(conversationId, request.message(), structured);
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            log.info("AI ask request completed successfully. endpoint=/api/ai/ask status=200 durationMs={} conversationId={}",
                    durationMs, conversationId);
            return structured;
        } catch (ResponseStatusException ex) {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            log.warn("AI ask request failed. endpoint=/api/ai/ask status={} durationMs={} conversationId={} reason={}",
                    ex.getStatusCode().value(), durationMs, conversationId, safeLogValue(ex.getReason()));
            throw ex;
        } catch (Exception failure) {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            throw mapFailure(failure, "/api/ai/ask", durationMs, conversationId);
        }
    }

    @Operation(
            summary = "Resend an edited user message",
            description = "Discards stored conversation exchanges after the first keepExchanges completed exchanges, then answers the edited message as a new branch. Scoped to the caller's HTTP session; other sessions are unaffected.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Dynamic structured AI response for the edited message",
                            content = @Content(schema = @Schema(implementation = DynamicAnswer.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid edit request"),
                    @ApiResponse(responseCode = "429", description = "AI service quota exhausted"),
                    @ApiResponse(responseCode = "502", description = "AI service returned an invalid or failed response"),
                    @ApiResponse(responseCode = "503", description = "AI service or network is unavailable"),
                    @ApiResponse(responseCode = "504", description = "AI service timed out")
            }
    )
    @PostMapping("/api/ai/edit")
    public DynamicAnswer edit(@Valid @RequestBody EditRequest request, HttpSession session) {
        String conversationId = session.getId();
        long startTime = System.nanoTime();

        try {
            chatBranchService.truncateToExchanges(conversationId, request.keepExchanges());
            DynamicAnswer structured = generateAnswer(request.message(), conversationId);
            persistHistory(conversationId, request.message(), structured);
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            log.info("AI edit request completed successfully. endpoint=/api/ai/edit status=200 durationMs={} keepExchanges={} conversationId={}",
                    durationMs, request.keepExchanges(), conversationId);
            return structured;
        } catch (ResponseStatusException ex) {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            log.warn("AI edit request failed. endpoint=/api/ai/edit status={} durationMs={} keepExchanges={} conversationId={} reason={}",
                    ex.getStatusCode().value(), durationMs, request.keepExchanges(), conversationId, safeLogValue(ex.getReason()));
            throw ex;
        } catch (Exception failure) {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            throw mapFailure(failure, "/api/ai/edit", durationMs, conversationId);
        }
    }

    private DynamicAnswer generateAnswer(String userMessage, String conversationId) {
        DynamicAnswer structured = chatClient.prompt()
                .user(userMessage)
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

        return structured;
    }

    private ResponseStatusException mapFailure(Exception failure, String endpoint, long durationMs, String conversationId) {
        ApiException apiFailure = findCause(failure, ApiException.class);
        if (apiFailure != null) {
            if (apiFailure.code() == 429) {
                if (hasProviderRetrySignal(apiFailure)) {
                    log.warn("Gemini returned HTTP 429 after bounded retries. endpoint={} status=429 durationMs={} conversationId={} providerDetails={}",
                            endpoint, durationMs, conversationId, safeLogValue(apiFailure.getMessage()));
                    return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                            "The AI service is handling high demand right now. Please wait a moment and try again.");
                }
                log.warn("Gemini returned HTTP 429 without a transient retry signal. endpoint={} status=429 durationMs={} conversationId={} providerDetails={}",
                        endpoint, durationMs, conversationId, safeLogValue(apiFailure.getMessage()));
                return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "AI service returned a rate limit response. Please try again later.");
            }
            if (apiFailure.code() == 404) {
                log.warn("Configured Gemini model or endpoint was not found (HTTP 404). endpoint={} status=502 durationMs={} conversationId={}",
                        endpoint, durationMs, conversationId);
                return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Configured AI model or endpoint is unavailable.");
            }
            if (apiFailure.code() == 503) {
                log.warn("Gemini provider remained unavailable after bounded retries (HTTP 503). endpoint={} status=503 durationMs={} conversationId={}. No fallback call made.",
                        endpoint, durationMs, conversationId);
                return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "AI service temporarily unavailable. Please try again later.");
            }
            log.warn("Gemini provider request failed. endpoint={} status=502 durationMs={} conversationId={} providerStatus={} providerDetails={}",
                    endpoint, durationMs, conversationId, apiFailure.code(), safeLogValue(apiFailure.getMessage()));
            return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "AI service could not complete the request. Please try again later.");
        }
        if (findCause(failure, SocketTimeoutException.class) != null
                || findCause(failure, HttpTimeoutException.class) != null
                || findCause(failure, TimeoutException.class) != null) {
            log.warn("Gemini request timed out. endpoint={} status=504 durationMs={} conversationId={}. No fallback call made.",
                    endpoint, durationMs, conversationId);
            return new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "AI service timed out. Please try again later.");
        }
        if (findCause(failure, UnknownHostException.class) != null
                || findCause(failure, ConnectException.class) != null
                || findCause(failure, NoRouteToHostException.class) != null) {
            log.warn("Gemini service is unreachable. endpoint={} status=503 durationMs={} conversationId={}. No fallback call made.",
                    endpoint, durationMs, conversationId);
            return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI service is unreachable. Please check network connectivity and try again.");
        }

        log.warn("AI response generation failed. endpoint={} status=502 durationMs={} conversationId={} failureType={} message={}",
                endpoint, durationMs, conversationId, failure.getClass().getSimpleName(), safeLogValue(failure.getMessage()));
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "AI service could not generate a valid response. Please try again later.");
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

    /** Detects a provider-sanctioned transient 429 from the SDK's flattened error details. */
    private static final Pattern PROVIDER_RETRY_DELAY =
            Pattern.compile("\"retryDelay\"\\s*:\\s*\"[0-9]+(?:\\.[0-9]+)?s\"");
    private static final Pattern PERMANENT_QUOTA_OR_BILLING_FAILURE = Pattern.compile(
            "(?i)(BILLING_DISABLED|BILLING_NOT_ENABLED|PAYMENT_REQUIRED|QUOTA_EXCEEDED|DAILY_LIMIT_EXCEEDED"
                    + "|SERVICE_DISABLED|CONSUMER_INVALID|PROJECT_DELETED|API_KEY_INVALID|API_KEY_SERVICE_BLOCKED"
                    + "|requests?[_\\s]+per[_\\s]+day|GenerateRequestsPerDay"
                    + "|billing.{0,24}(disabled|inactive)|payment required)");

    private static boolean hasProviderRetrySignal(ApiException apiFailure) {
        String detail = apiFailure.getMessage();
        return detail != null
                && PROVIDER_RETRY_DELAY.matcher(detail).find()
                && !PERMANENT_QUOTA_OR_BILLING_FAILURE.matcher(detail).find();
    }

    private static String safeLogValue(String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        String sanitized = value
                .replaceAll("(?i)AIza[0-9A-Za-z_-]{20,}", "[REDACTED]")
                .replaceAll("(?i)AQ\\.[0-9A-Za-z_-]{20,}", "[REDACTED]")
                .replaceAll("(?i)Bearer\\s+\\S+", "Bearer [REDACTED]")
                .replaceAll("(?i)key=[0-9A-Za-z_-]{20,}", "key=[REDACTED]")
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
        return sanitized.length() > 240 ? sanitized.substring(0, 240) + "..." : sanitized;
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
