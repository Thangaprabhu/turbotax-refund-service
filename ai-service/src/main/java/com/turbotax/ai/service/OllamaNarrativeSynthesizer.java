package com.turbotax.ai.service;

import com.turbotax.ai.domain.dto.response.GuidanceDoc;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Rewrites the retrieved guidance docs into one plain-English paragraph using a local Ollama
 * model, instead of the raw concatenation {@link #plainJoin} produces. Never lets a slow or
 * unavailable Ollama break a guidance response -- connection failures, timeouts, and empty
 * responses all fall back to the same plain join the stub always did, just logged as a warning.
 */
@Component
@Slf4j
public class OllamaNarrativeSynthesizer implements NarrativeSynthesizer {

    private static final String PROMPT_PREFIX = """
        Rewrite the following facts about a tax refund situation as one short, plain-English \
        paragraph for a taxpayer. Do not invent any new facts, dates, or amounts, and do not \
        give legal or tax advice -- just explain clearly what these facts mean:
        """;

    private final RestClient restClient;
    private final String model;

    public OllamaNarrativeSynthesizer(
        RestClient.Builder restClientBuilder,
        @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
        @Value("${ollama.model:llama3.2:3b}") String model
    ) {
        this.model = model;
        // Read/connect timeout is applied via RestClientConfig's RestClientCustomizer, not here --
        // setting it directly on the builder would overwrite MockRestServiceServer's request
        // factory in tests if this constructor ran after binding (see OllamaNarrativeSynthesizerTest).
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    @Override
    public String synthesize(List<GuidanceDoc> docs) {
        String facts = docs.stream().map(GuidanceDoc::content).collect(Collectors.joining("\n- ", "- ", ""));
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                .uri("/api/generate")
                .body(Map.of("model", model, "prompt", PROMPT_PREFIX + facts, "stream", false))
                .retrieve()
                .body(Map.class);

            Object text = response == null ? null : response.get("response");
            if (!(text instanceof String s) || s.isBlank()) {
                log.warn("Ollama returned no usable text, falling back to plain concatenation");
                return plainJoin(docs);
            }
            return s.trim();
        } catch (Exception e) {
            log.warn("Ollama narrative synthesis failed, falling back to plain concatenation: {}", e.getMessage());
            return plainJoin(docs);
        }
    }

    private String plainJoin(List<GuidanceDoc> docs) {
        return docs.stream().map(GuidanceDoc::content).collect(Collectors.joining(" "));
    }
}
