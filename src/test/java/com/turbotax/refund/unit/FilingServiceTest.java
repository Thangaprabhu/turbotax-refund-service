package com.turbotax.refund.unit;

import com.turbotax.refund.domain.dto.request.CreateFilingRequest;
import com.turbotax.refund.domain.dto.response.FilingResponse;
import com.turbotax.refund.domain.entity.Taxpayer;
import com.turbotax.refund.domain.enums.FormType;
import com.turbotax.refund.domain.enums.IrsStatus;
import com.turbotax.refund.domain.enums.TaxpayerType;
import com.turbotax.refund.dynamodb.FilingDynamoRepository;
import com.turbotax.refund.dynamodb.FilingItem;
import com.turbotax.refund.exception.TaxRefundException;
import com.turbotax.refund.kafka.producer.FilingEventProducer;
import com.turbotax.refund.mapper.FilingMapper;
import com.turbotax.refund.metrics.TaxMetrics;
import com.turbotax.refund.prediction.PredictionInput;
import com.turbotax.refund.prediction.RefundPrediction;
import com.turbotax.refund.prediction.RefundPredictor;
import com.turbotax.refund.repository.TaxpayerRepository;
import com.turbotax.refund.service.FilingService;
import com.turbotax.refund.service.TaxpayerService;
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
    @Mock TaxpayerRepository taxpayerRepository;
    @Mock TaxpayerService taxpayerService;
    @Mock FilingEventProducer eventProducer;
    @Mock TaxMetrics taxMetrics;
    @Mock FilingMapper filingMapper;
    @Mock RefundPredictor refundPredictor;

    FilingService filingService;

    UUID userId;
    UUID taxpayerId;

    @BeforeEach
    void setup() {
        filingService = new FilingService(filingRepo, taxpayerRepository, taxpayerService,
            eventProducer, taxMetrics, filingMapper, refundPredictor);
        userId = UUID.randomUUID();
        taxpayerId = UUID.randomUUID();

        // toResponse() mirrors the item's fields -- lets each test only set up what it cares about.
        lenient().when(filingMapper.toResponse(any(FilingItem.class))).thenAnswer(inv -> {
            FilingItem i = inv.getArgument(0);
            return new FilingResponse(i.getTaxpayerId(), i.getSk(), i.getTaxYear(), i.getFormType(),
                i.getJurisdiction(), i.getIrsStatus(), i.getFilingDate(), i.getExpectedDepositDate(),
                i.getAiPredictedDays(), i.getAiConfidence(), i.getLastSyncedAt(), i.getStatusHistory());
        });
    }

    private Taxpayer individualTaxpayer() {
        var t = new Taxpayer();
        t.setId(taxpayerId);
        t.setTaxpayerType(TaxpayerType.INDIVIDUAL);
        return t;
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
        var taxpayer = individualTaxpayer();

        when(taxpayerRepository.findById(taxpayerId)).thenReturn(Optional.of(taxpayer));
        when(filingRepo.findById(eq(taxpayerId.toString()), anyString())).thenReturn(Optional.empty());
        when(refundPredictor.predict(any(PredictionInput.class))).thenReturn(Optional.empty());

        var response = filingService.create(userId, taxpayerId, request);

        assertThat(response.sk()).isEqualTo("2024#F1040#FEDERAL");
        assertThat(response.irsStatus()).isEqualTo("RECEIVED");
        verify(taxpayerService).assertAccess(userId, taxpayerId);
        verify(filingRepo).save(any(FilingItem.class));
        verify(taxMetrics).incrementUserSubmittedTax();
        verify(taxMetrics).incrementExpectedFederalReturn();
        verify(taxMetrics, never()).incrementExpectedStateReturn();
        verify(eventProducer).publishFilingCreated(taxpayerId, TaxpayerType.INDIVIDUAL, FormType.F1040, "2024", "FEDERAL");
    }

    @Test
    void create_shouldIncrementStateMetric_andUseBmfAdapter_forBusinessNonFederal() {
        var request = new CreateFilingRequest(FormType.F1120, "2024", "CA", "2024-04-01");
        var taxpayer = new Taxpayer();
        taxpayer.setId(taxpayerId);
        taxpayer.setTaxpayerType(TaxpayerType.BUSINESS);

        when(taxpayerRepository.findById(taxpayerId)).thenReturn(Optional.of(taxpayer));
        when(filingRepo.findById(eq(taxpayerId.toString()), anyString())).thenReturn(Optional.empty());
        when(refundPredictor.predict(any(PredictionInput.class))).thenReturn(Optional.empty());

        filingService.create(userId, taxpayerId, request);

        verify(taxMetrics).incrementExpectedStateReturn();
        verify(taxMetrics, never()).incrementExpectedFederalReturn();

        var captor = org.mockito.ArgumentCaptor.forClass(FilingItem.class);
        verify(filingRepo).save(captor.capture());
        assertThat(captor.getValue().getAdapterUsed()).isEqualTo("IRS_BMF");
    }

    @Test
    void create_shouldThrowConflict_whenFilingAlreadyExists() {
        var request = new CreateFilingRequest(FormType.F1040, "2024", "FEDERAL", "2024-04-01");
        when(taxpayerRepository.findById(taxpayerId)).thenReturn(Optional.of(individualTaxpayer()));
        when(filingRepo.findById(eq(taxpayerId.toString()), anyString()))
            .thenReturn(Optional.of(item("2024", FormType.F1040, "FEDERAL", IrsStatus.RECEIVED)));

        assertThatThrownBy(() -> filingService.create(userId, taxpayerId, request))
            .isInstanceOf(TaxRefundException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    void create_shouldThrowNotFound_whenTaxpayerMissing() {
        var request = new CreateFilingRequest(FormType.F1040, "2024", "FEDERAL", "2024-04-01");
        when(taxpayerRepository.findById(taxpayerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> filingService.create(userId, taxpayerId, request))
            .isInstanceOf(TaxRefundException.class);
    }

    @Test
    void create_shouldSetPredictionFields_whenPredictorReturnsAResult() {
        var request = new CreateFilingRequest(FormType.F1040, "2024", "FEDERAL", "2024-04-01");
        when(taxpayerRepository.findById(taxpayerId)).thenReturn(Optional.of(individualTaxpayer()));
        when(filingRepo.findById(eq(taxpayerId.toString()), anyString())).thenReturn(Optional.empty());
        when(refundPredictor.predict(any(PredictionInput.class)))
            .thenReturn(Optional.of(new RefundPrediction(21, 0.55, "rules-v1")));

        var response = filingService.create(userId, taxpayerId, request);

        assertThat(response.aiPredictedDays()).isEqualTo(21);
        assertThat(response.aiConfidence()).isEqualTo(0.55);
        verify(taxMetrics).incrementRefundPredictionGenerated("rules-v1");
    }

    @Test
    void findAll_shouldBackfillAndPersist_whenPredictionMissing() {
        var stale = item("2024", FormType.F1040, "FEDERAL", IrsStatus.APPROVED);
        stale.setAiPredictedDays(null);
        stale.setAiModelVersion(null);

        when(filingRepo.findAllByTaxpayerId(taxpayerId.toString())).thenReturn(List.of(stale));
        when(refundPredictor.predict(any(PredictionInput.class)))
            .thenReturn(Optional.of(new RefundPrediction(3, 0.75, "rules-v1")));

        var result = filingService.findAll(userId, taxpayerId);

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

        when(filingRepo.findAllByTaxpayerId(taxpayerId.toString())).thenReturn(List.of(upToDate));
        when(refundPredictor.predict(any(PredictionInput.class)))
            .thenReturn(Optional.of(new RefundPrediction(3, 0.75, "rules-v1")));

        filingService.findAll(userId, taxpayerId);

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

        when(filingRepo.findAllByTaxpayerId(taxpayerId.toString())).thenReturn(List.of(staleVersion));
        when(refundPredictor.predict(any(PredictionInput.class)))
            .thenReturn(Optional.of(new RefundPrediction(3, 0.75, "rules-v1")));

        filingService.findAll(userId, taxpayerId);

        verify(filingRepo).save(staleVersion);
    }

    @Test
    void findAllPaginated_shouldSortByYearDescThenFormThenJurisdiction_andSlice() {
        var f1 = item("2023", FormType.F1040, "FEDERAL", IrsStatus.RECEIVED);
        var f2 = item("2024", FormType.F1065, "FEDERAL", IrsStatus.RECEIVED);
        var f3 = item("2024", FormType.F1040, "CA", IrsStatus.RECEIVED);

        when(filingRepo.findAllByTaxpayerId(taxpayerId.toString())).thenReturn(List.of(f1, f2, f3));
        when(refundPredictor.predict(any(PredictionInput.class))).thenReturn(Optional.empty());

        var page = filingService.findAllPaginated(userId, taxpayerId, 0, 2);

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
        when(filingRepo.findAllByTaxpayerId(taxpayerId.toString()))
            .thenReturn(List.of(item("2024", FormType.F1040, "FEDERAL", IrsStatus.RECEIVED)));
        when(refundPredictor.predict(any(PredictionInput.class))).thenReturn(Optional.empty());

        var page = filingService.findAllPaginated(userId, taxpayerId, 5, 10);

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    void findLatest_shouldReturnMostRecentByTaxYear() {
        var older = item("2022", FormType.F1040, "FEDERAL", IrsStatus.DEPOSITED);
        var newer = item("2024", FormType.F1040, "FEDERAL", IrsStatus.RECEIVED);

        when(filingRepo.findAllByTaxpayerId(taxpayerId.toString())).thenReturn(List.of(older, newer));
        when(refundPredictor.predict(any(PredictionInput.class))).thenReturn(Optional.empty());

        var result = filingService.findLatest(userId, taxpayerId);

        assertThat(result.taxYear()).isEqualTo("2024");
    }

    @Test
    void findLatest_shouldThrowNotFound_whenNoFilings() {
        when(filingRepo.findAllByTaxpayerId(taxpayerId.toString())).thenReturn(List.of());

        assertThatThrownBy(() -> filingService.findLatest(userId, taxpayerId))
            .isInstanceOf(TaxRefundException.class);
    }

    @Test
    void findByYear_shouldReturnFiling_whenFound() {
        var f = item("2024", FormType.F1040, "FEDERAL", IrsStatus.RECEIVED);
        when(filingRepo.findById(taxpayerId.toString(), "2024#F1040#FEDERAL")).thenReturn(Optional.of(f));
        when(refundPredictor.predict(any(PredictionInput.class))).thenReturn(Optional.empty());

        var result = filingService.findByYear(userId, taxpayerId, "2024", "f1040", "federal");

        assertThat(result.sk()).isEqualTo("2024#F1040#FEDERAL");
    }

    @Test
    void findByYear_shouldThrowNotFound_whenMissing() {
        when(filingRepo.findById(taxpayerId.toString(), "2024#F1040#FEDERAL")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> filingService.findByYear(userId, taxpayerId, "2024", "F1040", "FEDERAL"))
            .isInstanceOf(TaxRefundException.class);
    }

    @Test
    void updateStatus_shouldPersistAndPublishEvent_forFederal() {
        var f = item("2024", FormType.F1040, "FEDERAL", IrsStatus.RECEIVED);
        when(filingRepo.findById(taxpayerId.toString(), f.getSk())).thenReturn(Optional.of(f));
        when(refundPredictor.predict(any(PredictionInput.class)))
            .thenReturn(Optional.of(new RefundPrediction(3, 0.75, "rules-v1")));

        var result = filingService.updateStatus(userId, taxpayerId, f.getSk(), IrsStatus.APPROVED);

        assertThat(result.irsStatus()).isEqualTo("APPROVED");
        verify(taxMetrics).incrementFederalReturnStatusUpdated();
        verify(taxMetrics, never()).incrementStateReturnStatusUpdated();
        verify(eventProducer).publishStatusUpdated(taxpayerId.toString(), "2024", "F1040", "FEDERAL", "RECEIVED", "APPROVED");
    }

    @Test
    void updateStatus_shouldIncrementStateMetric_forNonFederal() {
        var f = item("2024", FormType.F1040, "CA", IrsStatus.RECEIVED);
        when(filingRepo.findById(taxpayerId.toString(), f.getSk())).thenReturn(Optional.of(f));
        when(refundPredictor.predict(any(PredictionInput.class))).thenReturn(Optional.empty());

        filingService.updateStatus(userId, taxpayerId, f.getSk(), IrsStatus.DEPOSITED);

        verify(taxMetrics).incrementStateReturnStatusUpdated();
        verify(taxMetrics, never()).incrementFederalReturnStatusUpdated();
    }

    @Test
    void updateStatus_shouldThrowNotFound_whenFilingMissing() {
        when(filingRepo.findById(taxpayerId.toString(), "2024#F1040#FEDERAL")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> filingService.updateStatus(userId, taxpayerId, "2024#F1040#FEDERAL", IrsStatus.APPROVED))
            .isInstanceOf(TaxRefundException.class);
    }
}
