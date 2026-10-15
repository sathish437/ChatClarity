package com.example.chat;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    public AiController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Operation(
            summary = "Send a message to the AI assistant",
            description = "Accepts a user message in the request body. Tracks conversation history per HTTP session and responds in the matching language and style (English, Tamil, Thanglish, or mixed). Returns a dynamic structured answer (DynamicAnswer) whose sections fit the question type.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Dynamic structured AI response",
                            content = @Content(schema = @Schema(implementation = DynamicAnswer.class))),
                    @ApiResponse(responseCode = "429", description = "AI service quota exhausted"),
                    @ApiResponse(responseCode = "500", description = "Error generating AI response")
            }
    )
    @PostMapping("/api/ai/ask")
    public DynamicAnswer ask(@RequestBody ChatRequest request, HttpSession session) {
        String conversationId = session.getId();
        String userMessage = (request != null && request.message() != null) ? request.message() : "";

        try {
            DynamicAnswer structured = chatClient.prompt()
                    .user(userMessage)
                    .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .entity(DynamicAnswer.class);

            if (structured == null) {
                throw new IllegalStateException("AI model returned an empty structured response");
            }
            return structured;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception entityFailure) {
            ApiException apiFailure = findApiException(entityFailure);
            if (apiFailure != null) {
                // The model/API call itself failed (quota, auth, provider outage):
                // never issue a second billable call as fallback.
                if (apiFailure.code() == 429) {
                    log.warn("Gemini quota exhausted (HTTP 429). No fallback call made.");
                    throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                            "AI service quota exhausted. Please try again later.");
                }
                if (apiFailure.code() == 503) {
                    log.warn("Gemini temporarily unavailable (HTTP 503). No fallback call made.");
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "AI service temporarily unavailable. Please try again later.");
                }
                log.error("AI provider call failed with HTTP {}. cause={}", apiFailure.code(),
                        entityFailure.toString(), entityFailure);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error generating AI response");
            }
            log.warn("Structured output conversion failed, falling back to plain text. cause={}",
                    entityFailure.toString(), entityFailure);
            try {
                String text = chatClient.prompt()
                        .user(userMessage)
                        .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                        .call()
                        .content();
                return new DynamicAnswer("general", text != null ? text : "", "Mixed",
                        null, null, null, null, null, null);
            } catch (Exception fallbackFailure) {
                log.error("AI response failed. cause={}", fallbackFailure.toString(), fallbackFailure);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error generating AI response", fallbackFailure);
            }
        }
    }

    /** Walks the cause chain for a Google GenAI provider failure; null when the model call itself succeeded. */
    private static ApiException findApiException(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ApiException apiException) {
                return apiException;
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
