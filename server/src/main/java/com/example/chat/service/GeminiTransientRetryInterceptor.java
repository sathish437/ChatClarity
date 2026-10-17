package com.example.chat.service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GeminiTransientRetryInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(GeminiTransientRetryInterceptor.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MILLIS = 1_000;
    private static final long MAX_BACKOFF_MILLIS = 8_000;
    private static final Duration MAX_PROVIDER_DELAY = Duration.ofSeconds(30);

    private final Sleeper sleeper;

    public GeminiTransientRetryInterceptor() {
        this(duration -> Thread.sleep(duration.toMillis()));
    }

    GeminiTransientRetryInterceptor(Sleeper sleeper) {
        this.sleeper = sleeper;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        for (int attempt = 1; ; attempt++) {
            Response response = chain.proceed(request);
            GeminiProviderError providerError = response.code() == 429 ? providerError(response) : null;
            if (attempt >= MAX_ATTEMPTS || !isRetryable(response, providerError)) {
                if (response.code() == 429) {
                    logQuotaDetails(response, providerError);
                }
                return response;
            }

            Duration delay = retryDelay(response, attempt, providerError);
            if (delay.compareTo(MAX_PROVIDER_DELAY) > 0) {
                if (response.code() == 429) {
                    logQuotaDetails(response, providerError);
                }
                log.warn("Gemini transient failure requested a delay above the configured limit; returning without retry.");
                return response;
            }

            log.warn("Retryable Gemini HTTP {}. attempt={} maxAttempts={} delayMs={}",
                    response.code(), attempt, MAX_ATTEMPTS, delay.toMillis());
            try {
                sleeper.sleep(delay);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                if (response.code() == 429) {
                    logQuotaDetails(response, providerError);
                }
                return response;
            }
            response.close();
        }
    }

    private static boolean isRetryable(Response response, GeminiProviderError providerError) {
        int status = response.code();
        if (status == 408 || status == 500 || status == 502 || status == 503 || status == 504) {
            return true;
        }
        return status == 429
                && providerError != null
                && !providerError.permanentQuotaOrBillingFailure()
                && hasTransientRetrySignal(response, providerError);
    }

    private static boolean hasTransientRetrySignal(Response response, GeminiProviderError providerError) {
        Duration headerDelay = parseRetryAfter(response.header("Retry-After"));
        if (headerDelay != null) {
            return headerDelay.compareTo(MAX_PROVIDER_DELAY) <= 0;
        }
        Duration bodyDelay = providerError.retryDelay();
        return bodyDelay != null && bodyDelay.compareTo(MAX_PROVIDER_DELAY) <= 0;
    }

    private static GeminiProviderError providerError(Response response) throws IOException {
        String body = response.body() == null ? "" : response.peekBody(64 * 1024).string();
        return GeminiProviderError.parse(body);
    }

    private static void logQuotaDetails(Response response, GeminiProviderError providerError) {
        Duration retryDelay = parseRetryAfter(response.header("Retry-After"));
        if (retryDelay == null) {
            retryDelay = providerError.retryDelay();
        }
        log.warn("Gemini HTTP 429. classification={} providerCode={} providerStatus={} providerMessage={} "
                        + "quotaReason={} quotaViolations={} retryDelayMs={}",
                providerError.classification(),
                providerError.code() == null ? "unknown" : providerError.code(),
                safeLogValue(providerError.status()),
                safeLogValue(providerError.message()),
                safeLogValue(providerError.reason()),
                safeQuotaViolations(providerError.quotaViolations()),
                retryDelay == null ? "none" : retryDelay.toMillis());
    }

    private static String safeQuotaViolations(List<GeminiProviderError.QuotaViolation> violations) {
        return violations.stream()
                .limit(3)
                .map(violation -> "subject=" + safeLogValue(violation.subject())
                        + ",description=" + safeLogValue(violation.description()))
                .toList()
                .toString();
    }

    private static String safeLogValue(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String sanitized = value
                .replaceAll("(?i)AIza[0-9A-Za-z_-]{20,}", "[REDACTED]")
                .replaceAll("(?i)Bearer\\s+\\S+", "Bearer [REDACTED]")
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
        return sanitized.length() > 240 ? sanitized.substring(0, 240) + "..." : sanitized;
    }

    private static Duration retryDelay(Response response, int attempt, GeminiProviderError providerError) {
        Duration providerDelay = parseRetryAfter(response.header("Retry-After"));
        if (providerDelay == null && providerError != null) {
            providerDelay = providerError.retryDelay();
        }
        if (providerDelay != null) {
            return providerDelay;
        }

        long maximum = Math.min(INITIAL_BACKOFF_MILLIS << (attempt - 1), MAX_BACKOFF_MILLIS);
        return Duration.ofMillis(ThreadLocalRandom.current().nextLong(
                Math.max(1, maximum / 2), maximum + maximum / 2 + 1));
    }

    private static Duration parseRetryAfter(String retryAfter) {
        if (retryAfter == null || retryAfter.isBlank()) {
            return null;
        }
        try {
            long seconds = Long.parseLong(retryAfter.trim());
            return seconds < 0 ? null : Duration.ofSeconds(seconds);
        } catch (NumberFormatException ignored) {
            try {
                Instant retryAt = ZonedDateTime.parse(
                        retryAfter.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                Duration delay = Duration.between(Instant.now(), retryAt);
                return delay.isNegative() ? Duration.ZERO : delay;
            } catch (DateTimeParseException invalidDate) {
                return null;
            }
        }
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }
}
