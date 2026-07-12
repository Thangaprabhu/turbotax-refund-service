package com.turbotax.ai.unit;

import com.turbotax.ai.domain.enums.FormType;
import com.turbotax.ai.domain.enums.IrsStatus;
import com.turbotax.ai.guidance.GuidanceDoc;
import com.turbotax.ai.guidance.RefundGuidanceRepository;
import com.turbotax.ai.guidance.RefundGuidanceService;
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

    RefundGuidanceService service;

    @org.junit.jupiter.api.BeforeEach
    void setup() {
        service = new RefundGuidanceService(repository);
    }

    @ParameterizedTest
    @EnumSource(value = IrsStatus.class, names = {"FLAGGED", "UNDER_REVIEW"}, mode = EnumSource.Mode.EXCLUDE)
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
            new GuidanceDoc(1L, "identity_verification", "First fact.", "https://irs.gov/a"),
            new GuidanceDoc(2L, "under_review_general", "Second fact.", "https://irs.gov/b")
        );
        when(repository.findTopDocIds("FLAGGED_INDIVIDUAL_FEDERAL")).thenReturn(Optional.of(List.of(1L, 2L)));
        when(repository.findDocsByIds(List.of(1L, 2L))).thenReturn(docs);

        var result = service.getGuidance(FormType.F1040, "FEDERAL", IrsStatus.FLAGGED);

        assertThat(result).isPresent();
        assertThat(result.get().situationKey()).isEqualTo("FLAGGED_INDIVIDUAL_FEDERAL");
        assertThat(result.get().narrative()).isEqualTo("First fact. Second fact.");
        assertThat(result.get().sources()).isEqualTo(docs);
    }
}
