package com.turbotax.refund.unit;

import com.turbotax.refund.controller.FilingController;
import com.turbotax.refund.domain.dto.request.CreateFilingRequest;
import com.turbotax.refund.domain.dto.request.UpdateFilingStatusRequest;
import com.turbotax.refund.domain.dto.response.FilingResponse;
import com.turbotax.refund.domain.dto.response.PageResponse;
import com.turbotax.refund.domain.enums.FormType;
import com.turbotax.refund.domain.enums.IrsStatus;
import com.turbotax.refund.guidance.GuidanceResponse;
import com.turbotax.refund.guidance.RefundGuidanceService;
import com.turbotax.refund.service.FilingService;
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
class FilingControllerTest {

    @Mock FilingService filingService;
    @Mock RefundGuidanceService refundGuidanceService;

    FilingController controller;
    UUID userId;
    UUID taxpayerId;

    @BeforeEach
    void setup() {
        controller = new FilingController(filingService, refundGuidanceService);
        userId = UUID.randomUUID();
        taxpayerId = UUID.randomUUID();
    }

    private FilingResponse filingResponse(String irsStatus, String formType, String jurisdiction) {
        return new FilingResponse(taxpayerId.toString(), "2024#" + formType + "#" + jurisdiction, "2024",
            formType, jurisdiction, irsStatus, "2024-04-01", null, null, null, null, List.of());
    }

    @Test
    void create_shouldDelegateToService() {
        var request = new CreateFilingRequest(FormType.F1040, "2024", "FEDERAL", "2024-04-01");
        var expected = filingResponse("RECEIVED", "F1040", "FEDERAL");
        when(filingService.create(userId, taxpayerId, request)).thenReturn(expected);

        var result = controller.create(userId.toString(), taxpayerId, request);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void listAll_shouldDelegateToService_withPageAndSize() {
        var expected = PageResponse.of(List.<FilingResponse>of(), 0, 10, 0);
        when(filingService.findAllPaginated(userId, taxpayerId, 0, 10)).thenReturn(expected);

        var result = controller.listAll(userId.toString(), taxpayerId, 0, 10);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void getLatest_shouldDelegateToService() {
        var expected = filingResponse("DEPOSITED", "F1040", "FEDERAL");
        when(filingService.findLatest(userId, taxpayerId)).thenReturn(expected);

        var result = controller.getLatest(userId.toString(), taxpayerId);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void getByYear_shouldDelegateToService() {
        var expected = filingResponse("APPROVED", "F1040", "FEDERAL");
        when(filingService.findByYear(userId, taxpayerId, "2024", "F1040", "FEDERAL")).thenReturn(expected);

        var result = controller.getByYear(userId.toString(), taxpayerId, "2024", "F1040", "FEDERAL");

        assertThat(result).isSameAs(expected);
    }

    @Test
    void getGuidance_shouldReturn200_whenGuidanceExists() {
        var filing = filingResponse("FLAGGED", "F1040", "FEDERAL");
        var guidance = new GuidanceResponse("FLAGGED_INDIVIDUAL_FEDERAL", "narrative", List.of());
        when(filingService.findByYear(userId, taxpayerId, "2024", "F1040", "FEDERAL")).thenReturn(filing);
        when(refundGuidanceService.getGuidance(FormType.F1040, "FEDERAL", IrsStatus.FLAGGED))
            .thenReturn(Optional.of(guidance));

        var result = controller.getGuidance(userId.toString(), taxpayerId, "2024", "F1040", "FEDERAL");

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isSameAs(guidance);
    }

    @Test
    void getGuidance_shouldReturn204_whenNoGuidanceApplies() {
        var filing = filingResponse("RECEIVED", "F1040", "FEDERAL");
        when(filingService.findByYear(userId, taxpayerId, "2024", "F1040", "FEDERAL")).thenReturn(filing);
        when(refundGuidanceService.getGuidance(FormType.F1040, "FEDERAL", IrsStatus.RECEIVED))
            .thenReturn(Optional.empty());

        var result = controller.getGuidance(userId.toString(), taxpayerId, "2024", "F1040", "FEDERAL");

        assertThat(result.getStatusCode().value()).isEqualTo(204);
        assertThat(result.getBody()).isNull();
    }

    @Test
    void updateStatus_shouldDelegateToService() {
        var request = new UpdateFilingStatusRequest(IrsStatus.APPROVED);
        var expected = filingResponse("APPROVED", "F1040", "FEDERAL");
        when(filingService.updateStatus(userId, taxpayerId, "2024#F1040#FEDERAL", IrsStatus.APPROVED))
            .thenReturn(expected);

        var result = controller.updateStatus(userId.toString(), taxpayerId, "2024#F1040#FEDERAL", request);

        assertThat(result).isSameAs(expected);
    }
}
