package com.turbotax.refund.unit;

import com.turbotax.refund.client.AiClient;
import com.turbotax.refund.domain.enums.FormType;
import com.turbotax.refund.domain.enums.IrsStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

class AiClientTest {

    private static final String BASE_URL = "http://ai-service.test";

    MockRestServiceServer mockServer;
    AiClient client;

    @BeforeEach
    void setup() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new AiClient(builder, BASE_URL);
    }

    @Test
    void predict_shouldReturnPrediction_whenAiServiceReturns200() {
        mockServer.expect(requestToUriTemplate(BASE_URL + "/api/v1/predictions"))
            .andExpect(method(POST))
            .andRespond(withSuccess("""
                {"predictedDays":21,"confidence":0.55,"modelVersion":"rules-v1"}
                """, MediaType.APPLICATION_JSON));

        var result = client.predict(FormType.F1040, "FEDERAL", IrsStatus.UNDER_REVIEW);

        assertThat(result).isPresent();
        assertThat(result.get().predictedDays()).isEqualTo(21);
        assertThat(result.get().modelVersion()).isEqualTo("rules-v1");
        mockServer.verify();
    }

    @Test
    void predict_shouldReturnEmpty_whenAiServiceReturns204() {
        mockServer.expect(requestToUriTemplate(BASE_URL + "/api/v1/predictions"))
            .andExpect(method(POST))
            .andRespond(withNoContent());

        var result = client.predict(FormType.F1040, "FEDERAL", IrsStatus.RECEIVED);

        assertThat(result).isEmpty();
    }

    @Test
    void getGuidance_shouldReturnGuidance_whenAiServiceReturns200() {
        mockServer.expect(method(GET))
            .andRespond(withSuccess("""
                {"situationKey":"FLAGGED_INDIVIDUAL_FEDERAL","narrative":"text","sources":[]}
                """, MediaType.APPLICATION_JSON));

        var result = client.getGuidance(FormType.F1040, "FEDERAL", IrsStatus.FLAGGED);

        assertThat(result).isPresent();
        assertThat(result.get().situationKey()).isEqualTo("FLAGGED_INDIVIDUAL_FEDERAL");
    }

    @Test
    void getGuidance_shouldReturnEmpty_whenAiServiceReturns204() {
        mockServer.expect(method(GET))
            .andRespond(withNoContent());

        var result = client.getGuidance(FormType.F1040, "FEDERAL", IrsStatus.RECEIVED);

        assertThat(result).isEmpty();
    }
}
