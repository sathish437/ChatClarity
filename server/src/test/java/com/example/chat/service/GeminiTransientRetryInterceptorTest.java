package com.example.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;

class GeminiTransientRetryInterceptorTest {

    private final List<Duration> waits = new ArrayList<>();
    private final GeminiTransientRetryInterceptor interceptor =
            new GeminiTransientRetryInterceptor(waits::add);

    @Test
    void retries503WithBoundedExponentialBackoff() throws IOException {
        Interceptor.Chain chain = chain(
                response(503),
                response(503),
                response(503));

        Response response = interceptor.intercept(chain);

        assertThat(response.code()).isEqualTo(503);
        verify(chain, times(3)).proceed(any(Request.class));
        assertThat(waits).hasSize(2);
        assertThat(waits.get(0)).isBetween(Duration.ofMillis(500), Duration.ofMillis(1500));
        assertThat(waits.get(1)).isBetween(Duration.ofSeconds(1), Duration.ofSeconds(3));
        assertThat(waits).allSatisfy(delay -> assertThat(delay).isLessThanOrEqualTo(Duration.ofSeconds(8)));
    }

    @Test
    void honorsRetryAfterHeaderFor503() throws IOException {
        Interceptor.Chain chain = chain(
                response(503, "Retry-After", "2"),
                response(200));

        Response response = interceptor.intercept(chain);

        assertThat(response.code()).isEqualTo(200);
        verify(chain, times(2)).proceed(any(Request.class));
        assertThat(waits).containsExactly(Duration.ofSeconds(2));
    }

    @Test
    void doesNotRetry429WithoutRetrySignal() throws IOException {
        Interceptor.Chain chain = chain(response(429));

        Response response = interceptor.intercept(chain);

        assertThat(response.code()).isEqualTo(429);
        verify(chain, times(1)).proceed(any(Request.class));
        assertThat(waits).isEmpty();
    }

    @Test
    void retriesTransient429WithProviderRetryDelay() throws IOException {
        Interceptor.Chain chain = chain(
                responseWithBody(429, rateLimitBody("2s")),
                response(200));

        Response response = interceptor.intercept(chain);

        assertThat(response.code()).isEqualTo(200);
        verify(chain, times(2)).proceed(any(Request.class));
        assertThat(waits).containsExactly(Duration.ofSeconds(2));
    }

    @Test
    void retries429WithRetryAfterHeader() throws IOException {
        Interceptor.Chain chain = chain(
                response(429, "Retry-After", "2"),
                response(200));

        Response response = interceptor.intercept(chain);

        assertThat(response.code()).isEqualTo(200);
        verify(chain, times(2)).proceed(any(Request.class));
        assertThat(waits).containsExactly(Duration.ofSeconds(2));
    }

    @Test
    void stopsRetrying429AfterMaxAttempts() throws IOException {
        Interceptor.Chain chain = chain(
                responseWithBody(429, rateLimitBody("1s")),
                responseWithBody(429, rateLimitBody("1s")),
                responseWithBody(429, rateLimitBody("1s")),
                responseWithBody(429, rateLimitBody("1s")));

        Response response = interceptor.intercept(chain);

        assertThat(response.code()).isEqualTo(429);
        verify(chain, times(3)).proceed(any(Request.class));
        assertThat(waits).hasSize(2);
    }

    @Test
    void doesNotRetry429WhenProviderDelayExceedsConfiguredLimit() throws IOException {
        Interceptor.Chain chain = chain(
                responseWithBody(429, rateLimitBody("120s")),
                response(200));

        Response response = interceptor.intercept(chain);

        assertThat(response.code()).isEqualTo(429);
        verify(chain, times(1)).proceed(any(Request.class));
        assertThat(waits).isEmpty();
    }

    @Test
    void doesNotRetryDailyQuotaEvenWhenProviderReturnsRetryDelay() throws IOException {
        Interceptor.Chain chain = chain(responseWithBody(429, dailyQuotaBody("5s")));

        Response response = interceptor.intercept(chain);

        assertThat(response.code()).isEqualTo(429);
        verify(chain, times(1)).proceed(any(Request.class));
        assertThat(waits).isEmpty();
    }

    @Test
    void doesNotRetryBillingFailureEvenWhenProviderReturnsRetryDelay() throws IOException {
        Interceptor.Chain chain = chain(responseWithBody(429, billingFailureBody("5s")));

        Response response = interceptor.intercept(chain);

        assertThat(response.code()).isEqualTo(429);
        verify(chain, times(1)).proceed(any(Request.class));
        assertThat(waits).isEmpty();
    }

    @Test
    void doesNotRetryExplicitQuotaExhaustionEvenWhenProviderReturnsRetryDelay() throws IOException {
        String body = "{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\","
                + "\"details\":["
                + "{\"@type\":\"type.googleapis.com/google.rpc.ErrorInfo\","
                + "\"reason\":\"QUOTA_EXCEEDED\"},"
                + "{\"@type\":\"type.googleapis.com/google.rpc.RetryInfo\","
                + "\"retryDelay\":\"5s\"}]}}";
        Interceptor.Chain chain = chain(responseWithBody(429, body));

        Response response = interceptor.intercept(chain);

        assertThat(response.code()).isEqualTo(429);
        verify(chain, times(1)).proceed(any(Request.class));
        assertThat(waits).isEmpty();
    }

    @Test
    void doesNotRetryProjectRestrictionEvenWhenProviderReturnsRetryDelay() throws IOException {
        String body = "{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\","
                + "\"details\":["
                + "{\"@type\":\"type.googleapis.com/google.rpc.ErrorInfo\","
                + "\"reason\":\"SERVICE_DISABLED\"},"
                + "{\"@type\":\"type.googleapis.com/google.rpc.RetryInfo\","
                + "\"retryDelay\":\"5s\"}]}}";
        Interceptor.Chain chain = chain(responseWithBody(429, body));

        Response response = interceptor.intercept(chain);

        assertThat(response.code()).isEqualTo(429);
        verify(chain, times(1)).proceed(any(Request.class));
        assertThat(waits).isEmpty();
    }

    @Test
    void doesNotRetry404ModelNotFound() throws IOException {
        Interceptor.Chain chain = chain(response(404));

        Response response = interceptor.intercept(chain);

        assertThat(response.code()).isEqualTo(404);
        verify(chain, times(1)).proceed(any(Request.class));
        assertThat(waits).isEmpty();
    }

    @Test
    void declinesRetryWhenProviderDelayExceedsConfiguredLimit() throws IOException {
        Interceptor.Chain chain = chain(
                response(503, "Retry-After", "60"),
                response(200));

        Response response = interceptor.intercept(chain);

        assertThat(response.code()).isEqualTo(503);
        verify(chain, times(1)).proceed(any(Request.class));
        assertThat(waits).isEmpty();
    }

    private static Interceptor.Chain chain(Response... responses) throws IOException {
        Interceptor.Chain chain = mock(Interceptor.Chain.class);
        AtomicInteger index = new AtomicInteger();
        when(chain.request()).thenReturn(request());
        when(chain.proceed(any(Request.class))).thenAnswer(invocation ->
                responses[Math.min(index.getAndIncrement(), responses.length - 1)]);
        return chain;
    }

    private static Request request() {
        return new Request.Builder().url("https://generativelanguage.googleapis.com/test").build();
    }

    private static Response response(int code) {
        return response(code, (String) null, null);
    }

    private static Response response(int code, String headerName, String headerValue) {
        Response.Builder builder = new Response.Builder()
                .request(request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(code == 200 ? "OK" : "Provider error")
                .body(ResponseBody.create("", MediaType.get("text/plain")));
        if (headerName != null) {
            builder.header(headerName, headerValue);
        }
        return builder.build();
    }

    private static Response responseWithBody(int code, String body) {
        return new Response.Builder()
                .request(request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("Provider error")
                .body(ResponseBody.create(body, MediaType.get("application/json")))
                .build();
    }

    private static String rateLimitBody(String retryDelay) {
        return "{\"error\":{\"code\":429,\"message\":\"Quota exceeded for metric.\","
                + "\"status\":\"RESOURCE_EXHAUSTED\",\"details\":["
                + "{\"@type\":\"type.googleapis.com/google.rpc.RetryInfo\","
                + "\"retryDelay\":\"" + retryDelay + "\"}]}}";
    }

    private static String dailyQuotaBody(String retryDelay) {
        return "{\"error\":{\"code\":429,\"message\":\"Daily request limit exceeded.\","
                + "\"status\":\"RESOURCE_EXHAUSTED\",\"details\":["
                + "{\"@type\":\"type.googleapis.com/google.rpc.QuotaFailure\","
                + "\"violations\":[{\"subject\":\"project/quotas/GenerateRequestsPerDay\","
                + "\"description\":\"Requests per day quota exceeded\"}]},"
                + "{\"@type\":\"type.googleapis.com/google.rpc.RetryInfo\","
                + "\"retryDelay\":\"" + retryDelay + "\"}]}}";
    }

    private static String billingFailureBody(String retryDelay) {
        return "{\"error\":{\"code\":429,\"message\":\"Billing is disabled for this project.\","
                + "\"status\":\"RESOURCE_EXHAUSTED\",\"details\":["
                + "{\"@type\":\"type.googleapis.com/google.rpc.ErrorInfo\","
                + "\"reason\":\"BILLING_DISABLED\"},"
                + "{\"@type\":\"type.googleapis.com/google.rpc.RetryInfo\","
                + "\"retryDelay\":\"" + retryDelay + "\"}]}}";
    }
}
