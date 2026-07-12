package com.turbotax.refund.client;

import com.turbotax.refund.domain.enums.FormType;
import com.turbotax.refund.domain.enums.IrsStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Component
@Slf4j
public class AiClient {

    private final RestClient restClient;

    public AiClient(RestClient.Builder restClientBuilder,
                     @Value("${services.ai-service.base-url}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    public Optional<RefundPrediction> predict(FormType formType, String jurisdiction, IrsStatus irsStatus) {
        ResponseEntity<RefundPrediction> response = restClient.post()
            .uri("/api/v1/predictions")
            .body(new PredictionRequest(formType, jurisdiction, irsStatus))
            .retrieve()
            .toEntity(RefundPrediction.class);
        return Optional.ofNullable(response.getBody());
    }

    public Optional<GuidanceResponse> getGuidance(FormType formType, String jurisdiction, IrsStatus irsStatus) {
        ResponseEntity<GuidanceResponse> response = restClient.get()
            .uri(uriBuilder -> uriBuilder.path("/api/v1/guidance")
                .queryParam("formType", formType)
                .queryParam("jurisdiction", jurisdiction)
                .queryParam("irsStatus", irsStatus)
                .build())
            .retrieve()
            .toEntity(GuidanceResponse.class);
        return Optional.ofNullable(response.getBody());
    }
}
