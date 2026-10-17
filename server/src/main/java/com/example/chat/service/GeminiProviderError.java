package com.example.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

record GeminiProviderError(
        Integer code,
        String status,
        String message,
        String reason,
        List<QuotaViolation> quotaViolations,
        Duration retryDelay,
        String classification,
        boolean permanentQuotaOrBillingFailure) {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern RETRY_DELAY =
            Pattern.compile("([0-9]+(?:\\.[0-9]+)?)s");

    static GeminiProviderError parse(String payload) {
        JsonNode root;
        try {
            root = MAPPER.readTree(payload);
        } catch (Exception invalidPayload) {
            return unspecified();
        }

        JsonNode error = root == null ? null : root.path("error");
        if (!error.isObject()) {
            return unspecified();
        }

        Integer code = error.path("code").isIntegralNumber() ? error.path("code").intValue() : null;
        String status = text(error.path("status"));
        String message = text(error.path("message"));
        String reason = null;
        Duration retryDelay = null;
        List<QuotaViolation> violations = new ArrayList<>();
        List<String> quotaIndicators = new ArrayList<>();

        JsonNode details = error.path("details");
        if (details.isArray()) {
            for (JsonNode detail : details) {
                String type = text(detail.path("@type"));
                if (type == null) {
                    continue;
                }
                if (type.endsWith("RetryInfo")) {
                    Duration parsedDelay = parseRetryDelay(text(detail.path("retryDelay")));
                    if (parsedDelay != null) {
                        retryDelay = parsedDelay;
                    }
                } else if (type.endsWith("ErrorInfo")) {
                    reason = text(detail.path("reason"));
                    JsonNode metadata = detail.path("metadata");
                    quotaIndicators.add(text(metadata.path("quotaMetric")));
                    quotaIndicators.add(text(metadata.path("quotaId")));
                } else if (type.endsWith("QuotaFailure")) {
                    JsonNode quotaItems = detail.path("violations");
                    if (quotaItems.isArray()) {
                        for (JsonNode item : quotaItems) {
                            String subject = text(item.path("subject"));
                            String description = text(item.path("description"));
                            if (subject != null || description != null) {
                                violations.add(new QuotaViolation(subject, description));
                                quotaIndicators.add(subject);
                                quotaIndicators.add(description);
                            }
                        }
                    }
                }
            }
        }

        boolean billingFailure = containsBillingFailure(reason, message);
        String classification = classify(reason, message, violations, quotaIndicators);
        boolean permanentFailure = billingFailure
                || "RPD".equals(classification)
                || isPermanentQuotaReason(reason)
                || isProjectRestriction(reason);
        return new GeminiProviderError(
                code, status, message, reason, List.copyOf(violations), retryDelay,
                classification, permanentFailure);
    }

    private static GeminiProviderError unspecified() {
        return new GeminiProviderError(null, null, null, null, List.of(), null, "UNSPECIFIED_429", false);
    }

    private static String classify(
            String reason,
            String message,
            List<QuotaViolation> violations,
            List<String> quotaIndicators) {
        if (containsBillingFailure(reason, message)) {
            return "BILLING_RESTRICTION";
        }
        if (isProjectRestriction(reason)) {
            return "PROJECT_RESTRICTION";
        }
        String quotaText = String.join(" ", quotaIndicators)
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[_./-]+", " ");
        if (quotaText.matches("(?s).*\\b(requests?\\s+per\\s+day|daily|rpd)\\b.*")) {
            return "RPD";
        }
        if (quotaText.matches("(?s).*\\b(tokens?\\s+per\\s+minute|tpm)\\b.*")) {
            return "TPM";
        }
        if (quotaText.matches("(?s).*\\b(requests?\\s+per\\s+minute|rpm)\\b.*")) {
            return "RPM";
        }
        if (isPermanentQuotaReason(reason)) {
            return "QUOTA_EXHAUSTED";
        }
        if (reason != null && reason.matches("(?i).*RATE_LIMIT.*")) {
            return "RATE_LIMIT_WINDOW_UNSPECIFIED";
        }
        if (!violations.isEmpty()) {
            return "QUOTA_WINDOW_UNSPECIFIED";
        }
        return "UNSPECIFIED_429";
    }

    private static boolean containsBillingFailure(String reason, String message) {
        String evidence = (reason == null ? "" : reason) + " " + (message == null ? "" : message);
        return evidence.matches("(?is).*\\b(billing[_\\s-]*(disabled|not enabled|inactive)|payment required)\\b.*");
    }

    private static boolean isPermanentQuotaReason(String reason) {
        return reason != null && reason.matches(
                "(?i)(QUOTA_EXCEEDED|DAILY_LIMIT_EXCEEDED|BILLING_DISABLED|BILLING_NOT_ENABLED)");
    }

    private static boolean isProjectRestriction(String reason) {
        return reason != null && reason.matches(
                "(?i)(SERVICE_DISABLED|CONSUMER_INVALID|PROJECT_DELETED|API_KEY_INVALID|API_KEY_SERVICE_BLOCKED)");
    }

    private static Duration parseRetryDelay(String value) {
        if (value == null) {
            return null;
        }
        var matcher = RETRY_DELAY.matcher(value);
        if (!matcher.matches()) {
            return null;
        }
        try {
            long millis = new BigDecimal(matcher.group(1)).movePointRight(3).longValueExact();
            return millis < 0 ? null : Duration.ofMillis(millis);
        } catch (ArithmeticException | NumberFormatException invalidDelay) {
            return null;
        }
    }

    private static String text(JsonNode node) {
        return node != null && node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
    }

    record QuotaViolation(String subject, String description) {
    }
}
