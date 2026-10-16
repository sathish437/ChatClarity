package com.example.chat.config;

import com.example.chat.service.GeminiTransientRetryInterceptor;
import com.google.genai.Client;
import com.google.genai.types.ClientOptions;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiConnectionProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Configuration
public class GeminiHttpClientConfiguration {

    @Bean
    Client googleGenAiClient(GoogleGenAiConnectionProperties connectionProperties) throws IOException {
        HttpRetryOptions retryOptions = HttpRetryOptions.builder()
                .attempts(1)
                .httpStatusCodes(List.of(408, 500, 502, 503, 504))
                .build();

        HttpOptions httpOptions = HttpOptions.builder()
                .retryOptions(retryOptions)
                .build();

        ClientOptions clientOptions = ClientOptions.builder()
                .customHttpClient(new okhttp3.OkHttpClient.Builder()
                        .addInterceptor(new GeminiTransientRetryInterceptor())
                        .build())
                .build();

        Client.Builder clientBuilder = Client.builder()
                .httpOptions(httpOptions)
                .clientOptions(clientOptions);

        boolean hasApiKey = StringUtils.hasText(connectionProperties.getApiKey());
        boolean hasProjectAndLocation = StringUtils.hasText(connectionProperties.getProjectId())
                && StringUtils.hasText(connectionProperties.getLocation());
        boolean useVertexAi = connectionProperties.isVertexAi() || (!hasApiKey && hasProjectAndLocation);

        if (useVertexAi) {
            Assert.hasText(connectionProperties.getProjectId(),
                    "Google GenAI project-id must be set for Vertex AI mode");
            Assert.hasText(connectionProperties.getLocation(),
                    "Google GenAI location must be set for Vertex AI mode");
            clientBuilder.vertexAI(true)
                    .project(connectionProperties.getProjectId())
                    .location(connectionProperties.getLocation());
            if (connectionProperties.getCredentialsUri() != null) {
                try (InputStream credentialsStream =
                        connectionProperties.getCredentialsUri().getInputStream()) {
                    clientBuilder.credentials(GoogleCredentials.fromStream(credentialsStream));
                }
            }
        } else if (hasApiKey) {
            clientBuilder.apiKey(connectionProperties.getApiKey());
        } else {
            throw new IllegalStateException(
                    "Incomplete Google GenAI configuration: provide an API key or Vertex AI project and location");
        }

        return clientBuilder.build();
    }
}
