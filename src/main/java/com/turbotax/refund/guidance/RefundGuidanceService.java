package com.turbotax.refund.guidance;

import com.turbotax.refund.domain.enums.FormType;
import com.turbotax.refund.domain.enums.IrsStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Retrieval-augmented guidance for "what to do about my refund issue" --
 * Option D in docs/ai-refund-prediction-scope.md.
 *
 * Retrieval is real: top_doc_ids for each situation were computed by an
 * actual pgvector cosine-similarity search at ingestion time (see
 * ml/rag/build_knowledge_base.py), not hardcoded here.
 *
 * Generation is a deliberate stub: {@link #synthesizeNarrative} assembles the
 * retrieved sources into readable text with light templating instead of
 * calling an LLM, so it costs nothing to run and can't hallucinate a wrong
 * fact. Swap that one method for a real LLM call over the same retrieved
 * sources when ready -- nothing else in this class needs to change.
 */
@Service
@RequiredArgsConstructor
public class RefundGuidanceService {

    private static final Set<IrsStatus> GUIDANCE_ELIGIBLE_STATUSES = EnumSet.of(IrsStatus.FLAGGED, IrsStatus.UNDER_REVIEW);

    private final RefundGuidanceRepository repository;

    public Optional<GuidanceResponse> getGuidance(FormType formType, String jurisdiction, IrsStatus status) {
        if (!GUIDANCE_ELIGIBLE_STATUSES.contains(status)) {
            return Optional.empty();
        }

        String situationKey = buildSituationKey(formType, jurisdiction, status);

        return repository.findTopDocIds(situationKey)
            .map(repository::findDocsByIds)
            .filter(docs -> !docs.isEmpty())
            .map(docs -> new GuidanceResponse(situationKey, synthesizeNarrative(docs), docs));
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

    private String synthesizeNarrative(List<GuidanceDoc> docs) {
        return docs.stream().map(GuidanceDoc::content).collect(Collectors.joining(" "));
    }
}
