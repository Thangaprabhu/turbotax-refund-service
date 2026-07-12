package com.turbotax.refund.client;

import com.turbotax.refund.domain.dto.response.TaxpayerResponse;
import com.turbotax.refund.exception.TaxRefundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
@Slf4j
public class TaxpayerClient {

    private final RestClient restClient;

    public TaxpayerClient(RestClient.Builder restClientBuilder,
                           @Value("${services.taxpayer-service.base-url}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    // Also doubles as the access check -- taxpayer-service returns 403/404 for a
    // caller that isn't the owner/delegate, or a taxpayer that doesn't exist.
    public TaxpayerResponse getTaxpayer(String bearerToken, UUID taxpayerId) {
        try {
            return restClient.get()
                .uri("/api/v1/taxpayers/{id}", taxpayerId)
                .header(HttpHeaders.AUTHORIZATION, bearerToken)
                .retrieve()
                .body(TaxpayerResponse.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw TaxRefundException.notFound("Taxpayer not found: " + taxpayerId);
        } catch (HttpClientErrorException.Forbidden e) {
            throw TaxRefundException.forbidden("Access denied to taxpayer: " + taxpayerId);
        } catch (HttpClientErrorException.Unauthorized e) {
            throw TaxRefundException.unauthorized("Invalid or expired token");
        }
    }
}
