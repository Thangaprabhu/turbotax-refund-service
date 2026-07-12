package com.turbotax.refund.service;

import com.turbotax.refund.domain.dto.request.CreateFilingRequest;
import com.turbotax.refund.domain.dto.response.FilingResponse;
import com.turbotax.refund.domain.dto.response.PageResponse;
import com.turbotax.refund.domain.enums.FormType;
import com.turbotax.refund.domain.enums.IrsStatus;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FilingService {

    private final FilingDynamoRepository filingRepo;
    private final TaxpayerRepository taxpayerRepository;
    private final TaxpayerService taxpayerService;
    private final FilingEventProducer eventProducer;
    private final TaxMetrics taxMetrics;
    private final FilingMapper filingMapper;
    private final RefundPredictor refundPredictor;

    @CacheEvict(value = "filings", key = "#taxpayerId")
    public FilingResponse create(UUID userId, UUID taxpayerId, CreateFilingRequest request) {
        taxpayerService.assertAccess(userId, taxpayerId);

        var taxpayer = taxpayerRepository.findById(taxpayerId)
            .orElseThrow(() -> TaxRefundException.notFound("Taxpayer not found"));

        String sk = FilingItem.buildSk(request.taxYear(), request.formType(), request.jurisdiction());

        if (filingRepo.findById(taxpayerId.toString(), sk).isPresent()) {
            throw TaxRefundException.conflict("Filing already exists for this year/form/jurisdiction");
        }

        FilingItem item = FilingItem.builder()
            .taxpayerId(taxpayerId.toString())
            .sk(sk)
            .taxYear(request.taxYear())
            .formType(request.formType().name())
            .jurisdiction(request.jurisdiction().toUpperCase())
            .irsStatus(IrsStatus.RECEIVED.name())
            .filingDate(request.filingDate())
            .lastSyncedAt(Instant.now().toString())
            .adapterUsed(taxpayer.getTaxpayerType().name().equals("INDIVIDUAL") ? "IRS_IMF" : "IRS_BMF")
            .build();

        applyPrediction(item);
        filingRepo.save(item);

        // Metrics
        taxMetrics.incrementUserSubmittedTax();
        if ("FEDERAL".equalsIgnoreCase(request.jurisdiction())) {
            taxMetrics.incrementExpectedFederalReturn();
        } else {
            taxMetrics.incrementExpectedStateReturn();
        }

        // Kafka event
        eventProducer.publishFilingCreated(taxpayerId, taxpayer.getTaxpayerType(),
            request.formType(), request.taxYear(), request.jurisdiction());

        log.info("Filing created: taxpayer={} sk={}", taxpayerId, sk);
        return filingMapper.toResponse(item);
    }

    @Cacheable(value = "filings", key = "#taxpayerId")
    public List<FilingResponse> findAll(UUID userId, UUID taxpayerId) {
        taxpayerService.assertAccess(userId, taxpayerId);
        return filingRepo.findAllByTaxpayerId(taxpayerId.toString())
            .stream()
            .map(this::backfillPredictionIfMissing)
            .map(filingMapper::toResponse)
            .toList();
    }

    /**
     * Pages over the same cached full list {@link #findAll} already produces, rather than
     * re-querying DynamoDB per page -- at this scale (a taxpayer's own filings) the full list
     * is cheap to hold in memory, and slicing here keeps the existing cache eviction semantics
     * on create/updateStatus untouched.
     */
    public PageResponse<FilingResponse> findAllPaginated(UUID userId, UUID taxpayerId, int page, int size) {
        List<FilingResponse> sorted = findAll(userId, taxpayerId).stream()
            .sorted(Comparator.comparing(FilingResponse::taxYear).reversed()
                .thenComparing(FilingResponse::formType)
                .thenComparing(FilingResponse::jurisdiction))
            .toList();

        int fromIndex = Math.min(page * size, sorted.size());
        int toIndex = Math.min(fromIndex + size, sorted.size());

        return PageResponse.of(sorted.subList(fromIndex, toIndex), page, size, sorted.size());
    }

    public FilingResponse findLatest(UUID userId, UUID taxpayerId) {
        taxpayerService.assertAccess(userId, taxpayerId);
        return filingRepo.findAllByTaxpayerId(taxpayerId.toString())
            .stream()
            .max(java.util.Comparator.comparing(FilingItem::getTaxYear))
            .map(this::backfillPredictionIfMissing)
            .map(filingMapper::toResponse)
            .orElseThrow(() -> TaxRefundException.notFound("No filings found"));
    }

    public FilingResponse findByYear(UUID userId, UUID taxpayerId,
                                     String taxYear, String formType, String jurisdiction) {
        taxpayerService.assertAccess(userId, taxpayerId);
        String sk = taxYear + "#" + formType.toUpperCase() + "#" + jurisdiction.toUpperCase();
        return filingRepo.findById(taxpayerId.toString(), sk)
            .map(this::backfillPredictionIfMissing)
            .map(filingMapper::toResponse)
            .orElseThrow(() -> TaxRefundException.notFound("Filing not found"));
    }

    @CacheEvict(value = "filings", key = "#taxpayerId")
    public FilingResponse updateStatus(UUID userId, UUID taxpayerId, String sk, IrsStatus newStatus) {
        taxpayerService.assertAccess(userId, taxpayerId);

        FilingItem item = filingRepo.findById(taxpayerId.toString(), sk)
            .orElseThrow(() -> TaxRefundException.notFound("Filing not found"));

        String oldStatus = item.getIrsStatus();
        item.setIrsStatus(newStatus.name());
        item.setLastSyncedAt(Instant.now().toString());
        applyPrediction(item);
        filingRepo.save(item);

        if ("FEDERAL".equalsIgnoreCase(item.getJurisdiction())) {
            taxMetrics.incrementFederalReturnStatusUpdated();
        } else {
            taxMetrics.incrementStateReturnStatusUpdated();
        }

        eventProducer.publishStatusUpdated(
            taxpayerId.toString(), item.getTaxYear(), item.getFormType(),
            item.getJurisdiction(), oldStatus, newStatus.name()
        );
        log.info("Filing status updated: taxpayer={} sk={} status={}", taxpayerId, sk, newStatus);
        return filingMapper.toResponse(item);
    }

    /**
     * Reconciles a filing's stored prediction against what the currently active predictor would
     * produce right now, and persists a correction if they differ. This covers rows that never got
     * a prediction (pre-dating this engine, or written directly against DynamoDB rather than through
     * {@link #updateStatus}), *and* rows carrying a stale prediction from a rule/version that no
     * longer applies (e.g. an older engine version, or a rule change like no-longer-predicting
     * RECEIVED). Cheap to run on every read since rules-v1 is a pure lookup, not a trained model.
     */
    private FilingItem backfillPredictionIfMissing(FilingItem item) {
        var input = new PredictionInput(
            FormType.valueOf(item.getFormType()),
            item.getJurisdiction(),
            IrsStatus.valueOf(item.getIrsStatus())
        );
        var current = refundPredictor.predict(input);

        boolean upToDate = Objects.equals(item.getAiPredictedDays(), current.map(RefundPrediction::predictedDays).orElse(null))
            && Objects.equals(item.getAiModelVersion(), current.map(RefundPrediction::modelVersion).orElse(null));

        if (!upToDate) {
            applyPredictionResult(item, current);
            filingRepo.save(item);
        }
        return item;
    }

    private Optional<RefundPrediction> applyPrediction(FilingItem item) {
        var input = new PredictionInput(
            FormType.valueOf(item.getFormType()),
            item.getJurisdiction(),
            IrsStatus.valueOf(item.getIrsStatus())
        );
        var prediction = refundPredictor.predict(input);
        applyPredictionResult(item, prediction);
        return prediction;
    }

    private void applyPredictionResult(FilingItem item, Optional<RefundPrediction> prediction) {
        item.setAiPredictedDays(prediction.map(RefundPrediction::predictedDays).orElse(null));
        item.setAiConfidence(prediction.map(RefundPrediction::confidence).orElse(null));
        item.setAiModelVersion(prediction.map(RefundPrediction::modelVersion).orElse(null));

        prediction.ifPresent(p -> taxMetrics.incrementRefundPredictionGenerated(p.modelVersion()));
    }
}
