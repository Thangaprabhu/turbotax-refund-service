package com.turbotax.ai.unit;

import com.turbotax.ai.domain.dto.response.GuidanceDoc;
import com.turbotax.ai.domain.enums.FormType;
import com.turbotax.ai.domain.enums.IrsStatus;
import com.turbotax.ai.repository.RefundGuidanceRepository;
import com.turbotax.ai.service.NarrativeSynthesizer;
import com.turbotax.ai.service.RefundGuidanceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundGuidanceServiceTest {

    @Mock RefundGuidanceRepository repository;
    @Mock NarrativeSynthesizer narrativeSynthesizer;

    RefundGuidanceService service;

    @org.junit.jupiter.api.BeforeEach
    void setup() {
        service = new RefundGuidanceService(repository, narrativeSynthesizer);
    }

    @ParameterizedTest
    @EnumSource(value = IrsStatus.class, names = {"FLAGGED", "UNDER_REVIEW", "APPROVED", "SENT"}, mode = EnumSource.Mode.EXCLUDE)
    void getGuidance_shouldReturnEmpty_forIneligibleStatuses(IrsStatus status) {
        var result = service.getGuidance(FormType.F1040, "FEDERAL", status);

        assertThat(result).isEmpty();
    }

    @Test
    void getGuidance_shouldBuildIndividualFederalKey() {
        when(repository.findTopDocIds("FLAGGED_INDIVIDUAL_FEDERAL")).thenReturn(Optional.empty());

        service.getGuidance(FormType.F1040, "FEDERAL", IrsStatus.FLAGGED);

        verify(repository).findTopDocIds("FLAGGED_INDIVIDUAL_FEDERAL");
    }

    @Test
    void getGuidance_shouldBuildKey_forApproved() {
        when(repository.findTopDocIds("APPROVED_INDIVIDUAL_FEDERAL")).thenReturn(Optional.empty());

        service.getGuidance(FormType.F1040, "FEDERAL", IrsStatus.APPROVED);

        verify(repository).findTopDocIds("APPROVED_INDIVIDUAL_FEDERAL");
    }

    @Test
    void getGuidance_shouldBuildKey_forSent() {
        when(repository.findTopDocIds("SENT_BUSINESS_STATE")).thenReturn(Optional.empty());

        service.getGuidance(FormType.F1120, "NY", IrsStatus.SENT);

        verify(repository).findTopDocIds("SENT_BUSINESS_STATE");
    }

    @Test
    void getGuidance_shouldBuildBusinessStateKey_forNonF1040FormAndNonFederalJurisdiction() {
        when(repository.findTopDocIds("UNDER_REVIEW_BUSINESS_STATE")).thenReturn(Optional.empty());

        var result = service.getGuidance(FormType.F1120, "CA", IrsStatus.UNDER_REVIEW);

        assertThat(result).isEmpty();
    }

    @Test
    void getGuidance_shouldReturnEmpty_whenNoSituationRowExists() {
        when(repository.findTopDocIds("FLAGGED_INDIVIDUAL_FEDERAL")).thenReturn(Optional.empty());

        assertThat(service.getGuidance(FormType.F1040, "FEDERAL", IrsStatus.FLAGGED)).isEmpty();
    }

    @Test
    void getGuidance_shouldReturnEmpty_whenDocsListIsEmpty() {
        when(repository.findTopDocIds("FLAGGED_INDIVIDUAL_FEDERAL")).thenReturn(Optional.of(List.of(1L, 2L)));
        when(repository.findDocsByIds(List.of(1L, 2L))).thenReturn(List.of());

        assertThat(service.getGuidance(FormType.F1040, "FEDERAL", IrsStatus.FLAGGED)).isEmpty();
    }

    @Test
    void getGuidance_shouldAssembleNarrativeFromRetrievedDocs() {
        var docs = List.of(
            new GuidanceDoc(1L, "identity_verification", "First fact.", "https://irs.gov/a", false),
            new GuidanceDoc(2L, "under_review_general", "Second fact.", "https://irs.gov/b", false)
        );
        when(repository.findTopDocIds("FLAGGED_INDIVIDUAL_FEDERAL")).thenReturn(Optional.of(List.of(1L, 2L)));
        when(repository.findDocsByIds(List.of(1L, 2L))).thenReturn(docs);
        when(narrativeSynthesizer.synthesize(docs)).thenReturn("First fact. Second fact.");

        var result = service.getGuidance(FormType.F1040, "FEDERAL", IrsStatus.FLAGGED);

        assertThat(result).isPresent();
        assertThat(result.get().situationKey()).isEqualTo("FLAGGED_INDIVIDUAL_FEDERAL");
        assertThat(result.get().narrative()).isEqualTo("First fact. Second fact.");
        assertThat(result.get().sources()).isEqualTo(docs);
    }

    @Test
    void getGuidance_shouldReorderDocs_toMatchRelevanceRankingNotSqlResultOrder() {
        var doc1 = new GuidanceDoc(1L, "identity_verification", "First fact.", "https://irs.gov/a", false);
        var doc2 = new GuidanceDoc(2L, "under_review_general", "Second fact.", "https://irs.gov/b", false);
        when(repository.findTopDocIds("FLAGGED_INDIVIDUAL_FEDERAL")).thenReturn(Optional.of(List.of(1L, 2L)));
        // Repository returns them out of order, as a plain SQL IN clause would.
        when(repository.findDocsByIds(List.of(1L, 2L))).thenReturn(List.of(doc2, doc1));

        var result = service.getGuidance(FormType.F1040, "FEDERAL", IrsStatus.FLAGGED);

        assertThat(result).isPresent();
        assertThat(result.get().sources()).containsExactly(doc1, doc2);
    }
}
