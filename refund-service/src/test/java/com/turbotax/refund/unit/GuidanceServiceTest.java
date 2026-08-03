package com.turbotax.refund.unit;

import com.turbotax.refund.client.AiClient;
import com.turbotax.refund.client.GuidanceResponse;
import com.turbotax.refund.domain.dto.response.FilingResponse;
import com.turbotax.refund.domain.enums.FormType;
import com.turbotax.refund.domain.enums.IrsStatus;
import com.turbotax.refund.service.FilingService;
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
class GuidanceServiceTest {

    @Mock FilingService filingService;
    @Mock AiClient aiClient;

    GuidanceService guidanceService;
    UUID taxpayerId;
    String bearerToken;

    @BeforeEach
    void setup() {
        guidanceService = new GuidanceService(filingService, aiClient);
        taxpayerId = UUID.randomUUID();
        bearerToken = "Bearer test-token";
    }

    private FilingResponse filingResponse(String irsStatus, String formType, String jurisdiction) {
        return new FilingResponse(taxpayerId.toString(), "2024#" + formType + "#" + jurisdiction, "2024",
            formType, jurisdiction, irsStatus, "2024-04-01", null, null, null, null, List.of());
    }

    @Test
    void getGuidance_shouldReturnGuidance_whenStatusIsEligible() {
        var filing = filingResponse("FLAGGED", "F1040", "FEDERAL");
        var guidance = new GuidanceResponse("FLAGGED_INDIVIDUAL_FEDERAL", "narrative", List.of());
        when(filingService.findByYear(bearerToken, taxpayerId, "2024", "F1040", "FEDERAL")).thenReturn(filing);
        when(aiClient.getGuidance(FormType.F1040, "FEDERAL", IrsStatus.FLAGGED)).thenReturn(Optional.of(guidance));

        var result = guidanceService.getGuidance(bearerToken, taxpayerId, "2024", "F1040", "FEDERAL");

        assertThat(result).isPresent().contains(guidance);
    }

    @Test
    void getGuidance_shouldReturnEmpty_whenStatusDoesNotNeedGuidance() {
        var filing = filingResponse("RECEIVED", "F1040", "FEDERAL");
        when(filingService.findByYear(bearerToken, taxpayerId, "2024", "F1040", "FEDERAL")).thenReturn(filing);
        when(aiClient.getGuidance(FormType.F1040, "FEDERAL", IrsStatus.RECEIVED)).thenReturn(Optional.empty());

        var result = guidanceService.getGuidance(bearerToken, taxpayerId, "2024", "F1040", "FEDERAL");

        assertThat(result).isEmpty();
    }
}
