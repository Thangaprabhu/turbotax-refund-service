package com.turbotax.refund.unit;

import com.turbotax.refund.controller.FilingController;
import com.turbotax.refund.domain.dto.request.CreateFilingRequest;
import com.turbotax.refund.domain.dto.response.FilingResponse;
import com.turbotax.refund.domain.dto.response.PageResponse;
import com.turbotax.refund.domain.enums.FormType;
import com.turbotax.refund.service.FilingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FilingControllerTest {

    @Mock FilingService filingService;

    FilingController controller;
    UUID taxpayerId;
    String bearerToken;

    @BeforeEach
    void setup() {
        controller = new FilingController(filingService);
        taxpayerId = UUID.randomUUID();
        bearerToken = "Bearer test-token";
    }

    private FilingResponse filingResponse(String irsStatus, String formType, String jurisdiction) {
        return new FilingResponse(taxpayerId.toString(), "2024#" + formType + "#" + jurisdiction, "2024",
            formType, jurisdiction, irsStatus, "2024-04-01", null, null, null, null, List.of());
    }

    @Test
    void create_shouldDelegateToService() {
        var request = new CreateFilingRequest(FormType.F1040, "2024", "FEDERAL", "2024-04-01");
        var expected = filingResponse("RECEIVED", "F1040", "FEDERAL");
        when(filingService.create(bearerToken, taxpayerId, request)).thenReturn(expected);

        var result = controller.create(bearerToken, taxpayerId, request);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void listAll_shouldDelegateToService_withPageAndSize() {
        var expected = PageResponse.of(List.<FilingResponse>of(), 0, 10, 0);
        when(filingService.findAllPaginated(bearerToken, taxpayerId, 0, 10)).thenReturn(expected);

        var result = controller.listAll(bearerToken, taxpayerId, 0, 10);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void getLatest_shouldDelegateToService() {
        var expected = filingResponse("DEPOSITED", "F1040", "FEDERAL");
        when(filingService.findLatest(bearerToken, taxpayerId)).thenReturn(expected);

        var result = controller.getLatest(bearerToken, taxpayerId);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void getByYear_shouldDelegateToService() {
        var expected = filingResponse("APPROVED", "F1040", "FEDERAL");
        when(filingService.findByYear(bearerToken, taxpayerId, "2024", "F1040", "FEDERAL")).thenReturn(expected);

        var result = controller.getByYear(bearerToken, taxpayerId, "2024", "F1040", "FEDERAL");

        assertThat(result).isSameAs(expected);
    }
}
