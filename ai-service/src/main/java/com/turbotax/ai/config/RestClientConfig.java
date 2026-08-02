package com.turbotax.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/**
 * Ollama is the only RestClient consumer in ai-service today, so a single global timeout
 * customizer is fine. Applied via Spring's autoconfiguration -- unit tests that build a bare
 * RestClient.builder() directly (to bind MockRestServiceServer) never go through this, so the
 * mock's request factory never gets overwritten.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClientCustomizer ollamaTimeoutCustomizer(@Value("${ollama.timeout-ms:8000}") int timeoutMs) {
        return builder -> {
            var requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(timeoutMs);
            requestFactory.setReadTimeout(timeoutMs);
            builder.requestFactory(requestFactory);
        };
    }
}
