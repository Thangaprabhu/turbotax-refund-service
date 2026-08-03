package com.turbotax.refund.unit;

import com.turbotax.refund.client.AiClient;
import com.turbotax.refund.client.RefundPrediction;
import com.turbotax.refund.domain.enums.FormType;
import com.turbotax.refund.domain.enums.IrsStatus;
import com.turbotax.refund.dynamodb.FilingDynamoRepository;
import com.turbotax.refund.dynamodb.FilingItem;
import com.turbotax.refund.metrics.TaxMetrics;
import com.turbotax.refund.service.FilingPredictionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FilingPredictionServiceTest {

    @Mock AiClient aiClient;
    @Mock FilingDynamoRepository filingRepo;
    @Mock TaxMetrics taxMetrics;

    FilingPredictionService predictionService;

    @BeforeEach
    void setup() {
        predictionService = new FilingPredictionService(aiClient, filingRepo, taxMetrics);
    }

    private FilingItem item(IrsStatus status) {
        return FilingItem.builder()
            .taxpayerId(UUID.randomUUID().toString())
            .sk(FilingItem.buildSk("2024", FormType.F1040, "FEDERAL"))
            .taxYear("2024")
            .formType(FormType.F1040.name())
            .jurisdiction("FEDERAL")
            .irsStatus(status.name())
            .filingDate("2024-04-01")
            .build();
    }

    @Test
    void applyPrediction_shouldSetFields_whenPredictorReturnsAResult() {
        var item = item(IrsStatus.RECEIVED);
        when(aiClient.predict(any(), anyString(), any()))
            .thenReturn(Optional.of(new RefundPrediction(21, 0.55, "rules-v1")));

        predictionService.applyPrediction(item);

        assertThat(item.getAiPredictedDays()).isEqualTo(21);
        assertThat(item.getAiConfidence()).isEqualTo(0.55);
        assertThat(item.getAiModelVersion()).isEqualTo("rules-v1");
        verify(taxMetrics).incrementRefundPredictionGenerated("rules-v1");
    }

    @Test
    void applyPrediction_shouldLeaveFieldsNull_whenPredictorReturnsNothing() {
        var item = item(IrsStatus.DEPOSITED);
        when(aiClient.predict(any(), anyString(), any())).thenReturn(Optional.empty());

        predictionService.applyPrediction(item);

        assertThat(item.getAiPredictedDays()).isNull();
        assertThat(item.getAiModelVersion()).isNull();
        verify(taxMetrics, never()).incrementRefundPredictionGenerated(anyString());
    }

    @Test
    void backfillIfMissing_shouldPersistCorrection_whenPredictionMissing() {
        var stale = item(IrsStatus.APPROVED);
        stale.setAiPredictedDays(null);
        stale.setAiModelVersion(null);
        when(aiClient.predict(any(), anyString(), any()))
            .thenReturn(Optional.of(new RefundPrediction(3, 0.75, "rules-v1")));

        var result = predictionService.backfillIfMissing(stale);

        assertThat(result.getAiPredictedDays()).isEqualTo(3);
        verify(filingRepo).save(stale);
    }

    @Test
    void backfillIfMissing_shouldNotPersist_whenAlreadyUpToDate() {
        var upToDate = item(IrsStatus.APPROVED);
        upToDate.setAiPredictedDays(3);
        upToDate.setAiConfidence(0.75);
        upToDate.setAiModelVersion("rules-v1");
        when(aiClient.predict(any(), anyString(), any()))
            .thenReturn(Optional.of(new RefundPrediction(3, 0.75, "rules-v1")));

        predictionService.backfillIfMissing(upToDate);

        verify(filingRepo, never()).save(any());
    }

    @Test
    void backfillIfMissing_shouldPersist_whenDaysMatchButModelVersionIsStale() {
        // Exercises the short-circuit && separately from the "missing" case above: predictedDays
        // already agrees, but modelVersion doesn't -- both operands of the && must be checked.
        var staleVersion = item(IrsStatus.APPROVED);
        staleVersion.setAiPredictedDays(3);
        staleVersion.setAiConfidence(0.75);
        staleVersion.setAiModelVersion("rules-v0");
        when(aiClient.predict(any(), anyString(), any()))
            .thenReturn(Optional.of(new RefundPrediction(3, 0.75, "rules-v1")));

        predictionService.backfillIfMissing(staleVersion);

        verify(filingRepo).save(staleVersion);
    }

    @Test
    void backfillIfMissing_shouldReturnTheSameItem() {
        var item = item(IrsStatus.RECEIVED);
        when(aiClient.predict(any(), anyString(), any())).thenReturn(Optional.empty());

        var result = predictionService.backfillIfMissing(item);

        assertThat(result).isSameAs(item);
    }
}
