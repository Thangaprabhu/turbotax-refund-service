package com.turbotax.refund.unit;

import com.turbotax.refund.client.TaxpayerClient;
import com.turbotax.refund.exception.TaxRefundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

class TaxpayerClientTest {

    private static final String BASE_URL = "http://taxpayer-service.test";

    MockRestServiceServer mockServer;
    TaxpayerClient client;

    @BeforeEach
    void setup() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new TaxpayerClient(builder, BASE_URL);
    }

    @Test
    void getTaxpayer_shouldReturnBody_onSuccess() {
        var taxpayerId = UUID.randomUUID();
        mockServer.expect(requestTo(BASE_URL + "/api/v1/taxpayers/" + taxpayerId))
            .andExpect(header("Authorization", "Bearer test-token"))
            .andRespond(withSuccess("""
                {"id":"%s","taxpayerType":"INDIVIDUAL","displayName":"Jane Doe","entityType":null,"stateOfReg":null,"createdAt":null}
                """.formatted(taxpayerId), MediaType.APPLICATION_JSON));

        var result = client.getTaxpayer("Bearer test-token", taxpayerId);

        assertThat(result.id()).isEqualTo(taxpayerId);
        assertThat(result.displayName()).isEqualTo("Jane Doe");
        mockServer.verify();
    }

    @Test
    void getTaxpayer_shouldThrowNotFound_on404() {
        var taxpayerId = UUID.randomUUID();
        mockServer.expect(requestTo(BASE_URL + "/api/v1/taxpayers/" + taxpayerId))
            .andRespond(withStatus(NOT_FOUND));

        assertThatThrownBy(() -> client.getTaxpayer("Bearer test-token", taxpayerId))
            .isInstanceOf(TaxRefundException.class)
            .satisfies(ex -> assertThat(((TaxRefundException) ex).getStatus().value()).isEqualTo(404));
    }

    @Test
    void getTaxpayer_shouldThrowForbidden_on403() {
        var taxpayerId = UUID.randomUUID();
        mockServer.expect(requestTo(BASE_URL + "/api/v1/taxpayers/" + taxpayerId))
            .andRespond(withStatus(FORBIDDEN));

        assertThatThrownBy(() -> client.getTaxpayer("Bearer test-token", taxpayerId))
            .isInstanceOf(TaxRefundException.class)
            .satisfies(ex -> assertThat(((TaxRefundException) ex).getStatus().value()).isEqualTo(403));
    }

    @Test
    void getTaxpayer_shouldThrowUnauthorized_on401() {
        var taxpayerId = UUID.randomUUID();
        mockServer.expect(requestTo(BASE_URL + "/api/v1/taxpayers/" + taxpayerId))
            .andRespond(withStatus(UNAUTHORIZED));

        assertThatThrownBy(() -> client.getTaxpayer("Bearer test-token", taxpayerId))
            .isInstanceOf(TaxRefundException.class)
            .satisfies(ex -> assertThat(((TaxRefundException) ex).getStatus().value()).isEqualTo(401));
    }
}
