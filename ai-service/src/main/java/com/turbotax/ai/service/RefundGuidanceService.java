package com.turbotax.ai.service;

import com.turbotax.ai.domain.dto.response.GuidanceDoc;
import com.turbotax.ai.domain.dto.response.GuidanceResponse;
import com.turbotax.ai.domain.enums.FormType;
import com.turbotax.ai.domain.enums.IrsStatus;
import com.turbotax.ai.repository.RefundGuidanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Retrieval-augmented guidance for "what to do about my refund issue" --
 * Option D in docs/ai-refund-prediction-scope.md.
 *
 * Retrieval is real: top_doc_ids for each situation were computed by an
 * actual pgvector cosine-similarity search at ingestion time (see
 * ml/rag/build_knowledge_base.py), not hardcoded here.
 *
 * Generation is delegated to {@link NarrativeSynthesizer}, not done here --
 * {@link OllamaNarrativeSynthesizer} rewrites the retrieved sources into one
 * paragraph via a local Ollama model, falling back to plain concatenation on
 * any failure so a slow or unavailable Ollama never breaks a guidance response.
 */
@Service
@RequiredArgsConstructor
public class RefundGuidanceService {

    // Everything except RECEIVED (too early for any signal) and DEPOSITED (refund already
    // landed -- nothing left to guide someone through).
    private static final Set<IrsStatus> GUIDANCE_ELIGIBLE_STATUSES =
        EnumSet.of(IrsStatus.FLAGGED, IrsStatus.UNDER_REVIEW, IrsStatus.APPROVED, IrsStatus.SENT);

    private final RefundGuidanceRepository repository;
    private final NarrativeSynthesizer narrativeSynthesizer;

    public Optional<GuidanceResponse> getGuidance(FormType formType, String jurisdiction, IrsStatus status) {
        if (!GUIDANCE_ELIGIBLE_STATUSES.contains(status)) {
            return Optional.empty();
        }

        String situationKey = buildSituationKey(formType, jurisdiction, status);

        return repository.findTopDocIds(situationKey)
            .map(ids -> inRelevanceOrder(ids, repository.findDocsByIds(ids)))
            .filter(docs -> !docs.isEmpty())
            .map(docs -> new GuidanceResponse(situationKey, narrativeSynthesizer.synthesize(docs), docs));
    }

    private String buildSituationKey(FormType formType, String jurisdiction, IrsStatus status) {
        boolean isIndividual = formType == FormType.F1040;
        boolean isFederal = "FEDERAL".equalsIgnoreCase(jurisdiction);
        return "%s_%s_%s".formatted(
            status.name(),
            isIndividual ? "INDIVIDUAL" : "BUSINESS",
            isFederal ? "FEDERAL" : "STATE"
        );
    }

    /**
     * The repository's SQL IN clause doesn't guarantee result order, so relevance ranking
     * (the whole point of the precomputed similarity search) has to be restored here.
     */
    private List<GuidanceDoc> inRelevanceOrder(List<Long> ids, List<GuidanceDoc> docs) {
        return ids.stream()
            .map(id -> docs.stream().filter(d -> d.id() == id).findFirst().orElse(null))
            .filter(Objects::nonNull)
            .toList();
    }
}
