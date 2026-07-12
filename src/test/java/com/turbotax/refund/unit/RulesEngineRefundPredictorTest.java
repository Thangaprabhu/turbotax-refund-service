package com.turbotax.refund.unit;

import com.turbotax.refund.domain.enums.FormType;
import com.turbotax.refund.domain.enums.IrsStatus;
import com.turbotax.refund.prediction.PredictionInput;
import com.turbotax.refund.prediction.RefundPrediction;
import com.turbotax.refund.prediction.RulesEngineRefundPredictor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RulesEngineRefundPredictorTest {

    private final RulesEngineRefundPredictor predictor = new RulesEngineRefundPredictor();

    @Test
    void predict_returnsEmpty_whenReceived_tooEarlyForASignal() {
        var input = new PredictionInput(FormType.F1040, "FEDERAL", IrsStatus.RECEIVED);

        assertThat(predictor.predict(input)).isEmpty();
    }

    @Test
    void predict_appliesStateMultiplier_whenJurisdictionIsNotFederal() {
        var input = new PredictionInput(FormType.F1040, "CA", IrsStatus.UNDER_REVIEW);

        Optional<RefundPrediction> result = predictor.predict(input);

        // base 21 * 1.3 = 27.3 -> rounds to 27, plus the 30-day review penalty
        assertThat(result).isPresent();
        assertThat(result.get().predictedDays()).isEqualTo(27 + 30);
    }

    @Test
    void predict_returnsShortFixedWindow_onceApproved() {
        var input = new PredictionInput(FormType.F1040, "FEDERAL", IrsStatus.APPROVED);

        Optional<RefundPrediction> result = predictor.predict(input);

        assertThat(result).isPresent();
        assertThat(result.get().predictedDays()).isEqualTo(3);
        assertThat(result.get().confidence()).isEqualTo(0.75);
    }

    @Test
    void predict_returnsShortestFixedWindow_onceSent() {
        var input = new PredictionInput(FormType.F1040, "FEDERAL", IrsStatus.SENT);

        Optional<RefundPrediction> result = predictor.predict(input);

        assertThat(result).isPresent();
        assertThat(result.get().predictedDays()).isEqualTo(2);
        assertThat(result.get().confidence()).isEqualTo(0.85);
    }

    @Test
    void predict_addsReviewPenalty_andLowersConfidence_whenUnderReview() {
        var input = new PredictionInput(FormType.F1040, "FEDERAL", IrsStatus.UNDER_REVIEW);

        Optional<RefundPrediction> result = predictor.predict(input);

        assertThat(result).isPresent();
        assertThat(result.get().predictedDays()).isEqualTo(21 + 30);
        assertThat(result.get().confidence()).isEqualTo(0.35);
    }

    @Test
    void predict_addsLargerPenalty_andLowestConfidence_whenFlagged() {
        var input = new PredictionInput(FormType.F1040, "FEDERAL", IrsStatus.FLAGGED);

        Optional<RefundPrediction> result = predictor.predict(input);

        assertThat(result).isPresent();
        assertThat(result.get().predictedDays()).isEqualTo(21 + 45);
        assertThat(result.get().confidence()).isEqualTo(0.25);
    }

    @Test
    void predict_returnsEmpty_whenAlreadyDeposited() {
        var input = new PredictionInput(FormType.F1040, "FEDERAL", IrsStatus.DEPOSITED);

        assertThat(predictor.predict(input)).isEmpty();
    }

    @Test
    void predict_usesLongerBaseline_forBusinessPayrollForm() {
        var input = new PredictionInput(FormType.F941, "FEDERAL", IrsStatus.UNDER_REVIEW);

        Optional<RefundPrediction> result = predictor.predict(input);

        assertThat(result).isPresent();
        assertThat(result.get().predictedDays()).isEqualTo(180 + 30);
    }

    @ParameterizedTest
    @EnumSource(value = IrsStatus.class, names = {"DEPOSITED", "RECEIVED"}, mode = EnumSource.Mode.EXCLUDE)
    void predict_alwaysReturnsPositiveDaysAndAConfidenceBetweenZeroAndOne(IrsStatus status) {
        var input = new PredictionInput(FormType.F1120, "NY", status);

        Optional<RefundPrediction> result = predictor.predict(input);

        assertThat(result).isPresent();
        assertThat(result.get().predictedDays()).isPositive();
        assertThat(result.get().confidence()).isBetween(0.0, 1.0);
    }
}
