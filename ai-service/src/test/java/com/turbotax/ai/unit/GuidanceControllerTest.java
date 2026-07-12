package com.turbotax.ai.unit;

import com.turbotax.ai.controller.GuidanceController;
import com.turbotax.ai.domain.enums.FormType;
import com.turbotax.ai.domain.enums.IrsStatus;
import com.turbotax.ai.guidance.GuidanceResponse;
import com.turbotax.ai.guidance.RefundGuidanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuidanceControllerTest {

    @Mock RefundGuidanceService refundGuidanceService;

    GuidanceController controller;

    @BeforeEach
    void setup() {
        controller = new GuidanceController(refundGuidanceService);
    }

    @Test
    void getGuidance_shouldReturn200WithBody_whenGuidanceExists() {
        var response = new GuidanceResponse("FLAGGED_INDIVIDUAL_FEDERAL", "narrative text", List.of());
        when(refundGuidanceService.getGuidance(FormType.F1040, "FEDERAL", IrsStatus.FLAGGED))
            .thenReturn(Optional.of(response));

        var result = controller.getGuidance(FormType.F1040, "FEDERAL", IrsStatus.FLAGGED);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void getGuidance_shouldReturn204_whenStatusDoesNotNeedGuidance() {
        when(refundGuidanceService.getGuidance(FormType.F1040, "FEDERAL", IrsStatus.RECEIVED))
            .thenReturn(Optional.empty());

        var result = controller.getGuidance(FormType.F1040, "FEDERAL", IrsStatus.RECEIVED);

        assertThat(result.getStatusCode().value()).isEqualTo(204);
        assertThat(result.getBody()).isNull();
    }
}
