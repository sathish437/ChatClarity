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
        assertThat(waits.get(1)).isGreaterThan(waits.get(0));
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
    void doesNotRetry429QuotaOrRateLimitErrors() throws IOException {
        Interceptor.Chain chain = chain(response(429));

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
}
