package com.turbotax.ai.unit;

import com.turbotax.ai.domain.dto.response.GuidanceDoc;
import com.turbotax.ai.service.OllamaNarrativeSynthesizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class OllamaNarrativeSynthesizerTest {

    MockRestServiceServer mockServer;
    OllamaNarrativeSynthesizer synthesizer;
    List<GuidanceDoc> docs;

    @BeforeEach
    void setup() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        synthesizer = new OllamaNarrativeSynthesizer(builder, "http://ollama.test", "llama3.2:3b");
        docs = List.of(
            new GuidanceDoc(1L, "identity_verification", "First fact.", "https://irs.gov/a", false),
            new GuidanceDoc(2L, "under_review_general", "Second fact.", "https://irs.gov/b", false)
        );
    }

    @Test
    void synthesize_shouldReturnOllamaText_whenCallSucceeds() {
        mockServer.expect(method(POST))
            .andRespond(withSuccess("""
                {"response":"Your return needs a quick identity check, which is routine."}
                """, MediaType.APPLICATION_JSON));

        var result = synthesizer.synthesize(docs);

        assertThat(result).isEqualTo("Your return needs a quick identity check, which is routine.");
    }

    @Test
    void synthesize_shouldFallBackToPlainJoin_whenOllamaReturnsServerError() {
        mockServer.expect(method(POST)).andRespond(withServerError());

        var result = synthesizer.synthesize(docs);

        assertThat(result).isEqualTo("First fact. Second fact.");
    }

    @Test
    void synthesize_shouldFallBackToPlainJoin_whenResponseFieldMissing() {
        mockServer.expect(method(POST))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        var result = synthesizer.synthesize(docs);

        assertThat(result).isEqualTo("First fact. Second fact.");
    }

    @Test
    void synthesize_shouldFallBackToPlainJoin_whenResponseFieldBlank() {
        mockServer.expect(method(POST))
            .andRespond(withSuccess("""
                {"response":"   "}
                """, MediaType.APPLICATION_JSON));

        var result = synthesizer.synthesize(docs);

        assertThat(result).isEqualTo("First fact. Second fact.");
    }
}
