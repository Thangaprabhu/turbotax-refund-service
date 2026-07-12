package com.turbotax.refund.prediction;

import com.turbotax.refund.domain.enums.FormType;
import com.turbotax.refund.domain.enums.IrsStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Deterministic "IRS cycle chart" predictor — Option A from docs/ai-refund-prediction-scope.md.
 *
 * No historical data or model training required: it encodes the IRS's own published
 * processing-time guidance as a lookup table, so it has no cold-start problem and can
 * ship before a single filing has completed its lifecycle. It is the permanent fallback
 * for later, data-driven engines (rules-v1 stays live for any filing shape a smarter
 * model isn't yet confident about).
 */
@Component
public class RulesEngineRefundPredictor implements RefundPredictor {

    public static final String MODEL_VERSION = "rules-v1";

    /**
     * Full RECEIVED -> DEPOSITED cycle in days for a FEDERAL e-filed return, by form type.
     * F1040: IRS publishes "9 out of 10 e-filed refunds within 21 days."
     * F1120 / F1065: no public cycle chart; IRS guidance for business returns commonly
     * cited in the 6-12 week range, so a wider, more conservative baseline is used.
     * F941: employment-tax adjustments (incl. ERC-related refunds) are documented by the
     * IRS as taking many months, so the baseline is set far longer.
     */
    private static final Map<FormType, Integer> FEDERAL_BASE_CYCLE_DAYS = new EnumMap<>(FormType.class);
    static {
        FEDERAL_BASE_CYCLE_DAYS.put(FormType.F1040, 21);
        FEDERAL_BASE_CYCLE_DAYS.put(FormType.F1120, 90);
        FEDERAL_BASE_CYCLE_DAYS.put(FormType.F1065, 90);
        FEDERAL_BASE_CYCLE_DAYS.put(FormType.F941, 180);
    }
    private static final int DEFAULT_FEDERAL_BASE_CYCLE_DAYS = 45;

    /** States don't publish a unified cycle chart and are generally slower/less automated than IRS e-file. */
    private static final double STATE_JURISDICTION_MULTIPLIER = 1.3;

    /** Fixed remaining-day estimates once a filing is past the "under review" stage of its cycle. */
    private static final int APPROVED_REMAINING_DAYS = 3; // approved -> sent, IRS-wide, not form-dependent
    private static final int SENT_REMAINING_DAYS = 2;     // sent -> deposited (ACH settlement)
    private static final int UNDER_REVIEW_EXTRA_DAYS = 30; // added on top of the base cycle
    private static final int FLAGGED_EXTRA_DAYS = 45;      // added on top of the base cycle

    private static final Map<IrsStatus, Double> STAGE_CONFIDENCE = new EnumMap<>(IrsStatus.class);
    static {
        STAGE_CONFIDENCE.put(IrsStatus.APPROVED, 0.75);
        STAGE_CONFIDENCE.put(IrsStatus.SENT, 0.85);
        STAGE_CONFIDENCE.put(IrsStatus.UNDER_REVIEW, 0.35);
        STAGE_CONFIDENCE.put(IrsStatus.FLAGGED, 0.25);
        // RECEIVED and DEPOSITED intentionally absent: no prediction applies at either end
        // of the lifecycle — RECEIVED carries no IRS signal yet beyond "filed", and
        // DEPOSITED means the refund has already landed.
    }

    @Override
    public Optional<RefundPrediction> predict(PredictionInput input) {
        if (input.irsStatus() == IrsStatus.DEPOSITED || input.irsStatus() == IrsStatus.RECEIVED) {
            return Optional.empty();
        }

        int baseCycleDays = FEDERAL_BASE_CYCLE_DAYS.getOrDefault(input.formType(), DEFAULT_FEDERAL_BASE_CYCLE_DAYS);
        if (!"FEDERAL".equalsIgnoreCase(input.jurisdiction())) {
            baseCycleDays = (int) Math.round(baseCycleDays * STATE_JURISDICTION_MULTIPLIER);
        }

        int predictedDays = switch (input.irsStatus()) {
            case APPROVED -> APPROVED_REMAINING_DAYS;
            case SENT -> SENT_REMAINING_DAYS;
            case UNDER_REVIEW -> baseCycleDays + UNDER_REVIEW_EXTRA_DAYS;
            case FLAGGED -> baseCycleDays + FLAGGED_EXTRA_DAYS;
            case RECEIVED, DEPOSITED -> throw new IllegalStateException("unreachable");
        };

        double confidence = STAGE_CONFIDENCE.get(input.irsStatus());

        return Optional.of(new RefundPrediction(predictedDays, confidence, MODEL_VERSION));
    }
}
