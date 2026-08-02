package com.turbotax.ai.unit;

import com.turbotax.ai.controller.PredictionController;
import com.turbotax.ai.domain.dto.response.RefundPrediction;
import com.turbotax.ai.domain.enums.FormType;
import com.turbotax.ai.domain.enums.IrsStatus;
import com.turbotax.ai.service.PredictionInput;
import com.turbotax.ai.service.RefundPredictor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PredictionControllerTest {

    @Mock RefundPredictor refundPredictor;

    PredictionController controller;

    @BeforeEach
    void setup() {
        controller = new PredictionController(refundPredictor);
    }

    @Test
    void predict_shouldReturn200WithBody_whenPredictorReturnsAPrediction() {
        var prediction = new RefundPrediction(51, 0.35, "rules-v1");
        when(refundPredictor.predict(new PredictionInput(FormType.F1040, "FEDERAL", IrsStatus.UNDER_REVIEW)))
            .thenReturn(Optional.of(prediction));

        var response = controller.predict(FormType.F1040, "FEDERAL", IrsStatus.UNDER_REVIEW);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(prediction);
    }

    @Test
    void predict_shouldReturn204_whenPredictorHasNoPrediction() {
        when(refundPredictor.predict(new PredictionInput(FormType.F1040, "FEDERAL", IrsStatus.RECEIVED)))
            .thenReturn(Optional.empty());

        var response = controller.predict(FormType.F1040, "FEDERAL", IrsStatus.RECEIVED);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(response.getBody()).isNull();
    }
}
