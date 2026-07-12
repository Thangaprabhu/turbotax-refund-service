package com.turbotax.refund.unit;

import com.turbotax.refund.controller.TaxpayerController;
import com.turbotax.refund.domain.dto.request.CreateTaxpayerRequest;
import com.turbotax.refund.domain.dto.response.PageResponse;
import com.turbotax.refund.domain.dto.response.TaxpayerResponse;
import com.turbotax.refund.domain.enums.TaxpayerType;
import com.turbotax.refund.service.TaxpayerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxpayerControllerTest {

    @Mock TaxpayerService taxpayerService;

    TaxpayerController controller;

    @BeforeEach
    void setup() {
        controller = new TaxpayerController(taxpayerService);
    }

    @Test
    void create_shouldDelegateToService_withParsedUserId() {
        var userId = UUID.randomUUID();
        var request = new CreateTaxpayerRequest(TaxpayerType.INDIVIDUAL, "123-45-6789", "Jane Doe", null, "CA");
        var expected = new TaxpayerResponse(UUID.randomUUID(), TaxpayerType.INDIVIDUAL, "Jane Doe", null, "CA", LocalDateTime.now());
        when(taxpayerService.create(userId, request)).thenReturn(expected);

        var result = controller.create(userId.toString(), request);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void listMine_shouldDelegateToService_withPageAndSize() {
        var userId = UUID.randomUUID();
        var expected = PageResponse.of(List.<TaxpayerResponse>of(), 1, 10, 0);
        when(taxpayerService.findAllForUser(userId, 1, 10)).thenReturn(expected);

        var result = controller.listMine(userId.toString(), 1, 10);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void getById_shouldDelegateToService() {
        var userId = UUID.randomUUID();
        var taxpayerId = UUID.randomUUID();
        var expected = new TaxpayerResponse(taxpayerId, TaxpayerType.BUSINESS, "Acme LLC", "LLC", "NY", LocalDateTime.now());
        when(taxpayerService.findById(userId, taxpayerId)).thenReturn(expected);

        var result = controller.getById(userId.toString(), taxpayerId);

        assertThat(result).isSameAs(expected);
    }
}
