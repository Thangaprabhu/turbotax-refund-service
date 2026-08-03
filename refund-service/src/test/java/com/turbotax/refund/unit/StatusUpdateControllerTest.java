package com.turbotax.refund.unit;

import com.turbotax.refund.controller.StatusUpdateController;
import com.turbotax.refund.domain.dto.request.UpdateFilingStatusRequest;
import com.turbotax.refund.domain.dto.response.FilingResponse;
import com.turbotax.refund.domain.enums.IrsStatus;
import com.turbotax.refund.service.StatusUpdateService;
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
class StatusUpdateControllerTest {

    @Mock StatusUpdateService statusUpdateService;

    StatusUpdateController controller;
    UUID taxpayerId;
    String bearerToken;

    @BeforeEach
    void setup() {
        controller = new StatusUpdateController(statusUpdateService);
        taxpayerId = UUID.randomUUID();
        bearerToken = "Bearer test-token";
    }

    @Test
    void updateStatus_shouldDelegateToService() {
        var request = new UpdateFilingStatusRequest(IrsStatus.APPROVED);
        var expected = new FilingResponse(taxpayerId.toString(), "2024#F1040#FEDERAL", "2024",
            "F1040", "FEDERAL", "APPROVED", "2024-04-01", null, null, null, null, List.of());
        when(statusUpdateService.updateStatus(bearerToken, taxpayerId, "2024#F1040#FEDERAL", IrsStatus.APPROVED))
            .thenReturn(expected);

        var result = controller.updateStatus(bearerToken, taxpayerId, "2024#F1040#FEDERAL", request);

        assertThat(result).isSameAs(expected);
    }
}
