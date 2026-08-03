package com.turbotax.refund.unit;

import com.turbotax.refund.client.TaxpayerClient;
import com.turbotax.refund.domain.dto.response.FilingResponse;
import com.turbotax.refund.domain.dto.response.TaxpayerResponse;
import com.turbotax.refund.domain.enums.FormType;
import com.turbotax.refund.domain.enums.IrsStatus;
import com.turbotax.refund.domain.enums.TaxpayerType;
import com.turbotax.refund.dynamodb.FilingDynamoRepository;
import com.turbotax.refund.dynamodb.FilingItem;
import com.turbotax.refund.exception.TaxRefundException;
import com.turbotax.refund.kafka.producer.FilingEventProducer;
import com.turbotax.refund.mapper.FilingMapper;
import com.turbotax.refund.metrics.TaxMetrics;
import com.turbotax.refund.service.FilingPredictionService;
import com.turbotax.refund.service.StatusUpdateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatusUpdateServiceTest {

    @Mock FilingDynamoRepository filingRepo;
    @Mock TaxpayerClient taxpayerClient;
    @Mock FilingEventProducer eventProducer;
    @Mock TaxMetrics taxMetrics;
    @Mock FilingMapper filingMapper;
    @Mock FilingPredictionService predictionService;

    StatusUpdateService statusUpdateService;

    UUID taxpayerId;
    String bearerToken;

    @BeforeEach
    void setup() {
        statusUpdateService = new StatusUpdateService(filingRepo, taxpayerClient, eventProducer, taxMetrics, filingMapper, predictionService);
        taxpayerId = UUID.randomUUID();
        bearerToken = "Bearer test-token";

        lenient().when(filingMapper.toResponse(any(FilingItem.class))).thenAnswer(inv -> {
            FilingItem i = inv.getArgument(0);
            return new FilingResponse(i.getTaxpayerId(), i.getSk(), i.getTaxYear(), i.getFormType(),
                i.getJurisdiction(), i.getIrsStatus(), i.getFilingDate(), i.getExpectedDepositDate(),
                i.getAiPredictedDays(), i.getAiConfidence(), i.getLastSyncedAt(), i.getStatusHistory());
        });
    }

    private TaxpayerResponse individualTaxpayer() {
        return new TaxpayerResponse(taxpayerId, TaxpayerType.INDIVIDUAL, "Test Filer", null, null, null);
    }

    private FilingItem item(String taxYear, FormType formType, String jurisdiction, IrsStatus status) {
        return FilingItem.builder()
            .taxpayerId(taxpayerId.toString())
            .sk(FilingItem.buildSk(taxYear, formType, jurisdiction))
            .taxYear(taxYear)
            .formType(formType.name())
            .jurisdiction(jurisdiction)
            .irsStatus(status.name())
            .filingDate(taxYear + "-04-01")
            .build();
    }

    @Test
    void updateStatus_shouldPersistAndPublishEvent_forFederal() {
        var f = item("2024", FormType.F1040, "FEDERAL", IrsStatus.RECEIVED);
        when(taxpayerClient.getTaxpayer(bearerToken, taxpayerId)).thenReturn(individualTaxpayer());
        when(filingRepo.findById(taxpayerId.toString(), f.getSk())).thenReturn(Optional.of(f));

        var result = statusUpdateService.updateStatus(bearerToken, taxpayerId, f.getSk(), IrsStatus.APPROVED);

        assertThat(result.irsStatus()).isEqualTo("APPROVED");
        verify(predictionService).applyPrediction(f);
        verify(taxMetrics).incrementFederalReturnStatusUpdated();
        verify(taxMetrics, never()).incrementStateReturnStatusUpdated();
        verify(eventProducer).publishStatusUpdated(taxpayerId.toString(), "2024", "F1040", "FEDERAL", "RECEIVED", "APPROVED");
    }

    @Test
    void updateStatus_shouldIncrementStateMetric_forNonFederal() {
        var f = item("2024", FormType.F1040, "CA", IrsStatus.RECEIVED);
        when(taxpayerClient.getTaxpayer(bearerToken, taxpayerId)).thenReturn(individualTaxpayer());
        when(filingRepo.findById(taxpayerId.toString(), f.getSk())).thenReturn(Optional.of(f));

        statusUpdateService.updateStatus(bearerToken, taxpayerId, f.getSk(), IrsStatus.DEPOSITED);

        verify(taxMetrics).incrementStateReturnStatusUpdated();
        verify(taxMetrics, never()).incrementFederalReturnStatusUpdated();
    }

    @Test
    void updateStatus_shouldThrowNotFound_whenFilingMissing() {
        when(taxpayerClient.getTaxpayer(bearerToken, taxpayerId)).thenReturn(individualTaxpayer());
        when(filingRepo.findById(taxpayerId.toString(), "2024#F1040#FEDERAL")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> statusUpdateService.updateStatus(bearerToken, taxpayerId, "2024#F1040#FEDERAL", IrsStatus.APPROVED))
            .isInstanceOf(TaxRefundException.class);
    }
}
