package com.turbotax.refund.unit;

import com.turbotax.refund.client.AiClient;
import com.turbotax.refund.client.RefundPrediction;
import com.turbotax.refund.client.TaxpayerClient;
import com.turbotax.refund.domain.dto.request.CreateFilingRequest;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FilingServiceTest {

    @Mock FilingDynamoRepository filingRepo;
    @Mock TaxpayerClient taxpayerClient;
    @Mock AiClient aiClient;
    @Mock FilingEventProducer eventProducer;
    @Mock TaxMetrics taxMetrics;
    @Mock FilingMapper filingMapper;

    FilingService filingService;

    UUID taxpayerId;
    String bearerToken;

    @BeforeEach
    void setup() {
        filingService = new FilingService(filingRepo, taxpayerClient, aiClient, eventProducer, taxMetrics, filingMapper);
        taxpayerId = UUID.randomUUID();
        bearerToken = "Bearer test-token";

        // toResponse() mirrors the item's fields -- lets each test only set up what it cares about.
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

    private TaxpayerResponse businessTaxpayer() {
        return new TaxpayerResponse(taxpayerId, TaxpayerType.BUSINESS, "Acme LLC", "LLC", null, null);
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
    void create_shouldBuildFilingAndPublishEvent_forFederalIndividual() {
        var request = new CreateFilingRequest(FormType.F1040, "2024", "FEDERAL", "2024-04-01");

        when(taxpayerClient.getTaxpayer(bearerToken, taxpayerId)).thenReturn(individualTaxpayer());
        when(filingRepo.findById(eq(taxpayerId.toString()), anyString())).thenReturn(Optional.empty());
        when(aiClient.predict(any(), anyString(), any())).thenReturn(Optional.empty());

        var response = filingService.create(bearerToken, taxpayerId, request);

        assertThat(response.sk()).isEqualTo("2024#F1040#FEDERAL");
        assertThat(response.irsStatus()).isEqualTo("RECEIVED");
        verify(filingRepo).save(any(FilingItem.class));
        verify(taxMetrics).incrementUserSubmittedTax();
        verify(taxMetrics).incrementExpectedFederalReturn();
        verify(taxMetrics, never()).incrementExpectedStateReturn();
        verify(eventProducer).publishFilingCreated(taxpayerId, TaxpayerType.INDIVIDUAL, FormType.F1040, "2024", "FEDERAL");
    }

    @Test
    void create_shouldIncrementStateMetric_andUseBmfAdapter_forBusinessNonFederal() {
        var request = new CreateFilingRequest(FormType.F1120, "2024", "CA", "2024-04-01");

        when(taxpayerClient.getTaxpayer(bearerToken, taxpayerId)).thenReturn(businessTaxpayer());
        when(filingRepo.findById(eq(taxpayerId.toString()), anyString())).thenReturn(Optional.empty());
        when(aiClient.predict(any(), anyString(), any())).thenReturn(Optional.empty());

        filingService.create(bearerToken, taxpayerId, request);

        verify(taxMetrics).incrementExpectedStateReturn();
        verify(taxMetrics, never()).incrementExpectedFederalReturn();

        var captor = org.mockito.ArgumentCaptor.forClass(FilingItem.class);
        verify(filingRepo).save(captor.capture());
        assertThat(captor.getValue().getAdapterUsed()).isEqualTo("IRS_BMF");
    }

    @Test
    void create_shouldThrowConflict_whenFilingAlreadyExists() {
        var request = new CreateFilingRequest(FormType.F1040, "2024", "FEDERAL", "2024-04-01");
        when(taxpayerClient.getTaxpayer(bearerToken, taxpayerId)).thenReturn(individualTaxpayer());
        when(filingRepo.findById(eq(taxpayerId.toString()), anyString()))
            .thenReturn(Optional.of(item("2024", FormType.F1040, "FEDERAL", IrsStatus.RECEIVED)));

        assertThatThrownBy(() -> filingService.create(bearerToken, taxpayerId, request))
            .isInstanceOf(TaxRefundException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    void create_shouldPropagateNotFound_whenTaxpayerClientThrows() {
        var request = new CreateFilingRequest(FormType.F1040, "2024", "FEDERAL", "2024-04-01");
        when(taxpayerClient.getTaxpayer(bearerToken, taxpayerId))
            .thenThrow(TaxRefundException.notFound("Taxpayer not found"));

        assertThatThrownBy(() -> filingService.create(bearerToken, taxpayerId, request))
            .isInstanceOf(TaxRefundException.class);
    }

    @Test
    void create_shouldSetPredictionFields_whenPredictorReturnsAResult() {
        var request = new CreateFilingRequest(FormType.F1040, "2024", "FEDERAL", "2024-04-01");
        when(taxpayerClient.getTaxpayer(bearerToken, taxpayerId)).thenReturn(individualTaxpayer());
        when(filingRepo.findById(eq(taxpayerId.toString()), anyString())).thenReturn(Optional.empty());
        when(aiClient.predict(any(), anyString(), any()))
            .thenReturn(Optional.of(new RefundPrediction(21, 0.55, "rules-v1")));

        var response = filingService.create(bearerToken, taxpayerId, request);

        assertThat(response.aiPredictedDays()).isEqualTo(21);
        assertThat(response.aiConfidence()).isEqualTo(0.55);
        verify(taxMetrics).incrementRefundPredictionGenerated("rules-v1");
    }

    @Test
    void findAll_shouldBackfillAndPersist_whenPredictionMissing() {
        var stale = item("2024", FormType.F1040, "FEDERAL", IrsStatus.APPROVED);
        stale.setAiPredictedDays(null);
        stale.setAiModelVersion(null);

        when(taxpayerClient.getTaxpayer(bearerToken, taxpayerId)).thenReturn(individualTaxpayer());
        when(filingRepo.findAllByTaxpayerId(taxpayerId.toString())).thenReturn(List.of(stale));
        when(aiClient.predict(any(), anyString(), any()))
            .thenReturn(Optional.of(new RefundPrediction(3, 0.75, "rules-v1")));

        var result = filingService.findAll(bearerToken, taxpayerId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).aiPredictedDays()).isEqualTo(3);
        verify(filingRepo).save(stale);
    }

    @Test
    void findAll_shouldNotPersist_whenPredictionAlreadyUpToDate() {
        var upToDate = item("2024", FormType.F1040, "FEDERAL", IrsStatus.APPROVED);
        upToDate.setAiPredictedDays(3);
        upToDate.setAiConfidence(0.75);
        upToDate.setAiModelVersion("rules-v1");

        when(taxpayerClient.getTaxpayer(bearerToken, taxpayerId)).thenReturn(individualTaxpayer());
        when(filingRepo.findAllByTaxpayerId(taxpayerId.toString())).thenReturn(List.of(upToDate));
        when(aiClient.predict(any(), anyString(), any()))
            .thenReturn(Optional.of(new RefundPrediction(3, 0.75, "rules-v1")));

        filingService.findAll(bearerToken, taxpayerId);

        verify(filingRepo, never()).save(any());
    }

    @Test
    void findAll_shouldPersist_whenDaysMatchButModelVersionIsStale() {
        // Exercises the short-circuit && separately from findAll_shouldBackfillAndPersist_whenPredictionMissing:
        // predictedDays already agrees, but modelVersion doesn't -- both operands of the && must be checked.
        var staleVersion = item("2024", FormType.F1040, "FEDERAL", IrsStatus.APPROVED);
        staleVersion.setAiPredictedDays(3);
        staleVersion.setAiConfidence(0.75);
        staleVersion.setAiModelVersion("rules-v0");

        when(taxpayerClient.getTaxpayer(bearerToken, taxpayerId)).thenReturn(individualTaxpayer());
        when(filingRepo.findAllByTaxpayerId(taxpayerId.toString())).thenReturn(List.of(staleVersion));
        when(aiClient.predict(any(), anyString(), any()))
            .thenReturn(Optional.of(new RefundPrediction(3, 0.75, "rules-v1")));

        filingService.findAll(bearerToken, taxpayerId);

        verify(filingRepo).save(staleVersion);
    }

    @Test
    void findAllPaginated_shouldSortByYearDescThenFormThenJurisdiction_andSlice() {
        var f1 = item("2023", FormType.F1040, "FEDERAL", IrsStatus.RECEIVED);
        var f2 = item("2024", FormType.F1065, "FEDERAL", IrsStatus.RECEIVED);
        var f3 = item("2024", FormType.F1040, "CA", IrsStatus.RECEIVED);

        when(taxpayerClient.getTaxpayer(bearerToken, taxpayerId)).thenReturn(individualTaxpayer());
        when(filingRepo.findAllByTaxpayerId(taxpayerId.toString())).thenReturn(List.of(f1, f2, f3));
        when(aiClient.predict(any(), anyString(), any())).thenReturn(Optional.empty());

        var page = filingService.findAllPaginated(bearerToken, taxpayerId, 0, 2);

        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.content()).hasSize(2);
        // 2024 filings sort before 2023 (desc); within 2024, F1040 sorts before F1065.
        assertThat(page.content().get(0).taxYear()).isEqualTo("2024");
        assertThat(page.content().get(0).formType()).isEqualTo("F1040");
        assertThat(page.content().get(1).taxYear()).isEqualTo("2024");
        assertThat(page.content().get(1).formType()).isEqualTo("F1065");
    }

    @Test
    void findAllPaginated_shouldReturnEmptyContent_whenPageOutOfRange() {
        when(taxpayerClient.getTaxpayer(bearerToken, taxpayerId)).thenReturn(individualTaxpayer());
        when(filingRepo.findAllByTaxpayerId(taxpayerId.toString()))
            .thenReturn(List.of(item("2024", FormType.F1040, "FEDERAL", IrsStatus.RECEIVED)));
        when(aiClient.predict(any(), anyString(), any())).thenReturn(Optional.empty());

        var page = filingService.findAllPaginated(bearerToken, taxpayerId, 5, 10);

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    void findLatest_shouldReturnMostRecentByTaxYear() {
        var older = item("2022", FormType.F1040, "FEDERAL", IrsStatus.DEPOSITED);
        var newer = item("2024", FormType.F1040, "FEDERAL", IrsStatus.RECEIVED);

        when(taxpayerClient.getTaxpayer(bearerToken, taxpayerId)).thenReturn(individualTaxpayer());
        when(filingRepo.findAllByTaxpayerId(taxpayerId.toString())).thenReturn(List.of(older, newer));
        when(aiClient.predict(any(), anyString(), any())).thenReturn(Optional.empty());

        var result = filingService.findLatest(bearerToken, taxpayerId);

        assertThat(result.taxYear()).isEqualTo("2024");
    }

    @Test
    void findLatest_shouldThrowNotFound_whenNoFilings() {
        when(taxpayerClient.getTaxpayer(bearerToken, taxpayerId)).thenReturn(individualTaxpayer());
        when(filingRepo.findAllByTaxpayerId(taxpayerId.toString())).thenReturn(List.of());

        assertThatThrownBy(() -> filingService.findLatest(bearerToken, taxpayerId))
            .isInstanceOf(TaxRefundException.class);
    }

    @Test
    void findByYear_shouldReturnFiling_whenFound() {
        var f = item("2024", FormType.F1040, "FEDERAL", IrsStatus.RECEIVED);
        when(taxpayerClient.getTaxpayer(bearerToken, taxpayerId)).thenReturn(individualTaxpayer());
        when(filingRepo.findById(taxpayerId.toString(), "2024#F1040#FEDERAL")).thenReturn(Optional.of(f));
        when(aiClient.predict(any(), anyString(), any())).thenReturn(Optional.empty());

        var result = filingService.findByYear(bearerToken, taxpayerId, "2024", "f1040", "federal");

        assertThat(result.sk()).isEqualTo("2024#F1040#FEDERAL");
    }

    @Test
    void findByYear_shouldThrowNotFound_whenMissing() {
        when(taxpayerClient.getTaxpayer(bearerToken, taxpayerId)).thenReturn(individualTaxpayer());
        when(filingRepo.findById(taxpayerId.toString(), "2024#F1040#FEDERAL")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> filingService.findByYear(bearerToken, taxpayerId, "2024", "F1040", "FEDERAL"))
            .isInstanceOf(TaxRefundException.class);
    }

    @Test
    void updateStatus_shouldPersistAndPublishEvent_forFederal() {
        var f = item("2024", FormType.F1040, "FEDERAL", IrsStatus.RECEIVED);
        when(taxpayerClient.getTaxpayer(bearerToken, taxpayerId)).thenReturn(individualTaxpayer());
        when(filingRepo.findById(taxpayerId.toString(), f.getSk())).thenReturn(Optional.of(f));
        when(aiClient.predict(any(), anyString(), any()))
            .thenReturn(Optional.of(new RefundPrediction(3, 0.75, "rules-v1")));

        var result = filingService.updateStatus(bearerToken, taxpayerId, f.getSk(), IrsStatus.APPROVED);

        assertThat(result.irsStatus()).isEqualTo("APPROVED");
        verify(taxMetrics).incrementFederalReturnStatusUpdated();
        verify(taxMetrics, never()).incrementStateReturnStatusUpdated();
        verify(eventProducer).publishStatusUpdated(taxpayerId.toString(), "2024", "F1040", "FEDERAL", "RECEIVED", "APPROVED");
    }

    @Test
    void updateStatus_shouldIncrementStateMetric_forNonFederal() {
        var f = item("2024", FormType.F1040, "CA", IrsStatus.RECEIVED);
        when(taxpayerClient.getTaxpayer(bearerToken, taxpayerId)).thenReturn(individualTaxpayer());
        when(filingRepo.findById(taxpayerId.toString(), f.getSk())).thenReturn(Optional.of(f));
        when(aiClient.predict(any(), anyString(), any())).thenReturn(Optional.empty());

        filingService.updateStatus(bearerToken, taxpayerId, f.getSk(), IrsStatus.DEPOSITED);

        verify(taxMetrics).incrementStateReturnStatusUpdated();
        verify(taxMetrics, never()).incrementFederalReturnStatusUpdated();
    }

    @Test
    void updateStatus_shouldThrowNotFound_whenFilingMissing() {
        when(taxpayerClient.getTaxpayer(bearerToken, taxpayerId)).thenReturn(individualTaxpayer());
        when(filingRepo.findById(taxpayerId.toString(), "2024#F1040#FEDERAL")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> filingService.updateStatus(bearerToken, taxpayerId, "2024#F1040#FEDERAL", IrsStatus.APPROVED))
            .isInstanceOf(TaxRefundException.class);
    }
}
