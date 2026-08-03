package com.turbotax.refund.service;

import com.turbotax.refund.client.AiClient;
import com.turbotax.refund.client.RefundPrediction;
import com.turbotax.refund.domain.enums.FormType;
import com.turbotax.refund.domain.enums.IrsStatus;
import com.turbotax.refund.dynamodb.FilingDynamoRepository;
import com.turbotax.refund.dynamodb.FilingItem;
import com.turbotax.refund.metrics.TaxMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * Shared ai-service prediction logic used by both filing creation and status updates, so the
 * write path (apply a fresh prediction before saving) and the read path (backfill a stale or
 * missing one) aren't duplicated across {@link FilingService} and {@link StatusUpdateService}.
 */
@Service
@RequiredArgsConstructor
public class FilingPredictionService {

    private final AiClient aiClient;
    private final FilingDynamoRepository filingRepo;
    private final TaxMetrics taxMetrics;

    public void applyPrediction(FilingItem item) {
        var prediction = aiClient.predict(
            FormType.valueOf(item.getFormType()), item.getJurisdiction(), IrsStatus.valueOf(item.getIrsStatus()));
        applyPredictionResult(item, prediction);
    }

    /**
     * Reconciles a filing's stored prediction against what ai-service would produce right now,
     * and persists a correction if they differ. This covers rows that never got a prediction
     * (pre-dating this engine, or written directly against DynamoDB rather than through
     * {@link StatusUpdateService#updateStatus}), *and* rows carrying a stale prediction from a
     * rule/version that no longer applies (e.g. an older engine version, or a rule change like
     * no-longer-predicting RECEIVED). Cheap to run on every read since rules-v1 is a pure
     * lookup, not a trained model.
     */
    public FilingItem backfillIfMissing(FilingItem item) {
        var current = aiClient.predict(
            FormType.valueOf(item.getFormType()), item.getJurisdiction(), IrsStatus.valueOf(item.getIrsStatus()));

        boolean upToDate = Objects.equals(item.getAiPredictedDays(), current.map(RefundPrediction::predictedDays).orElse(null))
            && Objects.equals(item.getAiModelVersion(), current.map(RefundPrediction::modelVersion).orElse(null));

        if (!upToDate) {
            applyPredictionResult(item, current);
            filingRepo.save(item);
        }
        return item;
    }

    private void applyPredictionResult(FilingItem item, Optional<RefundPrediction> prediction) {
        item.setAiPredictedDays(prediction.map(RefundPrediction::predictedDays).orElse(null));
        item.setAiConfidence(prediction.map(RefundPrediction::confidence).orElse(null));
        item.setAiModelVersion(prediction.map(RefundPrediction::modelVersion).orElse(null));

        prediction.ifPresent(p -> taxMetrics.incrementRefundPredictionGenerated(p.modelVersion()));
    }
}
