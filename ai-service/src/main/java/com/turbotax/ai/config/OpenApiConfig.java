package com.turbotax.ai.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("TurboTax Refund AI Service API")
                .version("v1")
                .description("Refund-timing prediction (rules engine) and RAG-based refund-issue guidance. "
                    + "Internal only — called by refund-service, never exposed to the client."));
    }
}
