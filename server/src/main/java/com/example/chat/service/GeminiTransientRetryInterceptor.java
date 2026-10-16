package com.example.chat.service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static final Pattern RETRY_DELAY = Pattern.compile(
            "\"retryDelay\"\\s*:\\s*\"([0-9]+(?:\\.[0-9]+)?)s\"");

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
            if (!isRetryableStatus(response.code()) || attempt >= MAX_ATTEMPTS) {
                return response;
            }

            Duration delay = retryDelay(response, attempt);
            if (delay.compareTo(MAX_PROVIDER_DELAY) > 0) {
                log.warn("Gemini transient failure requested a delay above the configured limit; returning without retry.");
                return response;
            }

            log.warn("Retryable Gemini HTTP {}. attempt={} maxAttempts={} delayMs={}",
                    response.code(), attempt, MAX_ATTEMPTS, delay.toMillis());
            try {
                sleeper.sleep(delay);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return response;
            }
            response.close();
        }
    }

    private static boolean isRetryableStatus(int status) {
        return status == 408 || status == 500 || status == 502 || status == 503 || status == 504;
    }

    private static Duration retryDelay(Response response, int attempt) throws IOException {
        Duration providerDelay = parseRetryAfter(response.header("Retry-After"));
        if (providerDelay == null && response.body() != null) {
            Matcher matcher = RETRY_DELAY.matcher(response.peekBody(64 * 1024).string());
            if (matcher.find()) {
                try {
                    providerDelay = Duration.ofMillis((long) (Double.parseDouble(matcher.group(1)) * 1000));
                } catch (NumberFormatException ignored) {
                    providerDelay = null;
                }
            }
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
