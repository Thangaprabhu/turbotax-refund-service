package com.turbotax.ai.prediction;

import java.util.Optional;

/**
 * Estimates remaining time-to-deposit for a filing. Implementations are swappable —
 * see docs/ai-refund-prediction-scope.md for the staged rollout (rules -> ML -> survival model).
 */
public interface RefundPredictor {

    /**
     * @return empty when no estimate applies (e.g. the filing is already DEPOSITED).
     */
    Optional<RefundPrediction> predict(PredictionInput input);
}
