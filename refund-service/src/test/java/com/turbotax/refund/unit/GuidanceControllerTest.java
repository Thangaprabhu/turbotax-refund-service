package com.turbotax.refund.unit;

import com.turbotax.refund.client.GuidanceResponse;
import com.turbotax.refund.controller.GuidanceController;
import com.turbotax.refund.service.GuidanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuidanceControllerTest {

    @Mock GuidanceService guidanceService;

    GuidanceController controller;
    UUID taxpayerId;
    String bearerToken;

    @BeforeEach
    void setup() {
        controller = new GuidanceController(guidanceService);
        taxpayerId = UUID.randomUUID();
        bearerToken = "Bearer test-token";
    }

    @Test
    void getGuidance_shouldReturn200_whenGuidanceExists() {
        var guidance = new GuidanceResponse("FLAGGED_INDIVIDUAL_FEDERAL", "narrative", List.of());
        when(guidanceService.getGuidance(bearerToken, taxpayerId, "2024", "F1040", "FEDERAL"))
            .thenReturn(Optional.of(guidance));

        var result = controller.getGuidance(bearerToken, taxpayerId, "2024", "F1040", "FEDERAL");

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isSameAs(guidance);
    }

    @Test
    void getGuidance_shouldReturn204_whenNoGuidanceApplies() {
        when(guidanceService.getGuidance(bearerToken, taxpayerId, "2024", "F1040", "FEDERAL"))
            .thenReturn(Optional.empty());

        var result = controller.getGuidance(bearerToken, taxpayerId, "2024", "F1040", "FEDERAL");

        assertThat(result.getStatusCode().value()).isEqualTo(204);
        assertThat(result.getBody()).isNull();
    }
}
