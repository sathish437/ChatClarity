package com.example.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class GeminiProviderErrorTest {

    @Test
    void parsesProviderStatusMessageQuotaViolationAndRetryDelay() {
        GeminiProviderError error = GeminiProviderError.parse("""
                {
                  "error": {
                    "code": 429,
                    "status": "RESOURCE_EXHAUSTED",
                    "message": "Request limit reached.",
                    "details": [
                      {
                        "@type": "type.googleapis.com/google.rpc.QuotaFailure",
                        "violations": [{
                          "subject": "projects/123/quotas/GenerateRequestsPerMinute",
                          "description": "Requests per minute quota exceeded."
                        }]
                      },
                      {
                        "@type": "type.googleapis.com/google.rpc.RetryInfo",
                        "retryDelay": "2.5s"
                      }
                    ]
                  }
                }
                """);

        assertThat(error.code()).isEqualTo(429);
        assertThat(error.status()).isEqualTo("RESOURCE_EXHAUSTED");
        assertThat(error.message()).isEqualTo("Request limit reached.");
        assertThat(error.classification()).isEqualTo("RPM");
        assertThat(error.quotaViolations()).containsExactly(
                new GeminiProviderError.QuotaViolation(
                        "projects/123/quotas/GenerateRequestsPerMinute",
                        "Requests per minute quota exceeded."));
        assertThat(error.retryDelay()).isEqualTo(Duration.ofMillis(2500));
        assertThat(error.permanentQuotaOrBillingFailure()).isFalse();
    }

    @Test
    void classifiesTokenAndDailyQuotaDimensions() {
        GeminiProviderError tokenQuota = GeminiProviderError.parse("""
                {"error":{"details":[{"@type":"type.googleapis.com/google.rpc.QuotaFailure",
                "violations":[{"description":"Tokens per minute exceeded"}]}]}}
                """);
        GeminiProviderError dailyQuota = GeminiProviderError.parse("""
                {"error":{"details":[{"@type":"type.googleapis.com/google.rpc.QuotaFailure",
                "violations":[{"subject":"GenerateRequestsPerDay"}]}]}}
                """);

        assertThat(tokenQuota.classification()).isEqualTo("TPM");
        assertThat(tokenQuota.permanentQuotaOrBillingFailure()).isFalse();
        assertThat(dailyQuota.classification()).isEqualTo("RPD");
        assertThat(dailyQuota.permanentQuotaOrBillingFailure()).isTrue();
    }

    @Test
    void identifiesBillingRestriction() {
        GeminiProviderError error = GeminiProviderError.parse("""
                {"error":{"status":"RESOURCE_EXHAUSTED","message":"Billing disabled.",
                "details":[{"@type":"type.googleapis.com/google.rpc.ErrorInfo",
                "reason":"BILLING_DISABLED"}]}}
                """);

        assertThat(error.classification()).isEqualTo("BILLING_RESTRICTION");
        assertThat(error.permanentQuotaOrBillingFailure()).isTrue();
    }

    @Test
    void identifiesProjectRestriction() {
        GeminiProviderError error = GeminiProviderError.parse("""
                {"error":{"code":429,"status":"RESOURCE_EXHAUSTED",
                "details":[{"@type":"type.googleapis.com/google.rpc.ErrorInfo",
                "reason":"SERVICE_DISABLED"}]}}
                """);

        assertThat(error.classification()).isEqualTo("PROJECT_RESTRICTION");
        assertThat(error.permanentQuotaOrBillingFailure()).isTrue();
    }

    @Test
    void missingOrInvalidErrorDetailsRemainUnspecified() {
        GeminiProviderError missing = GeminiProviderError.parse(
                "{\"error\":{\"code\":429}}");
        GeminiProviderError malformed = GeminiProviderError.parse("not-json");

        assertThat(missing.code()).isEqualTo(429);
        assertThat(missing.status()).isNull();
        assertThat(missing.message()).isNull();
        assertThat(missing.classification()).isEqualTo("UNSPECIFIED_429");
        assertThat(missing.retryDelay()).isNull();
        assertThat(missing.permanentQuotaOrBillingFailure()).isFalse();
        assertThat(malformed.classification()).isEqualTo("UNSPECIFIED_429");
    }
}
