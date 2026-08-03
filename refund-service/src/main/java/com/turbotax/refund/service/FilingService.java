package com.turbotax.refund.service;

import com.turbotax.refund.client.TaxpayerClient;
import com.turbotax.refund.domain.dto.request.CreateFilingRequest;
import com.turbotax.refund.domain.dto.response.FilingResponse;
import com.turbotax.refund.domain.dto.response.PageResponse;
import com.turbotax.refund.domain.dto.response.TaxpayerResponse;
import com.turbotax.refund.domain.enums.IrsStatus;
import com.turbotax.refund.dynamodb.FilingDynamoRepository;
import com.turbotax.refund.dynamodb.FilingItem;
import com.turbotax.refund.exception.TaxRefundException;
import com.turbotax.refund.kafka.producer.FilingEventProducer;
import com.turbotax.refund.mapper.FilingMapper;
import com.turbotax.refund.metrics.TaxMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FilingService {

    private final FilingDynamoRepository filingRepo;
    private final TaxpayerClient taxpayerClient;
    private final FilingEventProducer eventProducer;
    private final TaxMetrics taxMetrics;
    private final FilingMapper filingMapper;
    private final FilingPredictionService predictionService;

    @CacheEvict(value = "filings", key = "#taxpayerId")
    public FilingResponse create(String bearerToken, UUID taxpayerId, CreateFilingRequest request) {
        TaxpayerResponse taxpayer = taxpayerClient.getTaxpayer(bearerToken, taxpayerId);

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
            .adapterUsed(taxpayer.taxpayerType().name().equals("INDIVIDUAL") ? "IRS_IMF" : "IRS_BMF")
            .build();

        predictionService.applyPrediction(item);
        filingRepo.save(item);

        // Metrics
        taxMetrics.incrementUserSubmittedTax();
        if ("FEDERAL".equalsIgnoreCase(request.jurisdiction())) {
            taxMetrics.incrementExpectedFederalReturn();
        } else {
            taxMetrics.incrementExpectedStateReturn();
        }

        // Kafka event
        eventProducer.publishFilingCreated(taxpayerId, taxpayer.taxpayerType(),
            request.formType(), request.taxYear(), request.jurisdiction());

        log.info("Filing created: taxpayer={} sk={}", taxpayerId, sk);
        return filingMapper.toResponse(item);
    }

    @Cacheable(value = "filings", key = "#taxpayerId")
    public List<FilingResponse> findAll(String bearerToken, UUID taxpayerId) {
        taxpayerClient.getTaxpayer(bearerToken, taxpayerId);
        return filingRepo.findAllByTaxpayerId(taxpayerId.toString())
            .stream()
            .map(predictionService::backfillIfMissing)
            .map(filingMapper::toResponse)
            .toList();
    }

    /**
     * Pages over the same cached full list {@link #findAll} already produces, rather than
     * re-querying DynamoDB per page -- at this scale (a taxpayer's own filings) the full list
     * is cheap to hold in memory, and slicing here keeps the existing cache eviction semantics
     * on create/updateStatus untouched.
     */
    public PageResponse<FilingResponse> findAllPaginated(String bearerToken, UUID taxpayerId, int page, int size) {
        List<FilingResponse> sorted = findAll(bearerToken, taxpayerId).stream()
            .sorted(Comparator.comparing(FilingResponse::taxYear).reversed()
                .thenComparing(FilingResponse::formType)
                .thenComparing(FilingResponse::jurisdiction))
            .toList();

        int fromIndex = Math.min(page * size, sorted.size());
        int toIndex = Math.min(fromIndex + size, sorted.size());

        return PageResponse.of(sorted.subList(fromIndex, toIndex), page, size, sorted.size());
    }

    public FilingResponse findLatest(String bearerToken, UUID taxpayerId) {
        taxpayerClient.getTaxpayer(bearerToken, taxpayerId);
        return filingRepo.findAllByTaxpayerId(taxpayerId.toString())
            .stream()
            .max(Comparator.comparing(FilingItem::getTaxYear))
            .map(predictionService::backfillIfMissing)
            .map(filingMapper::toResponse)
            .orElseThrow(() -> TaxRefundException.notFound("No filings found"));
    }

    public FilingResponse findByYear(String bearerToken, UUID taxpayerId,
                                     String taxYear, String formType, String jurisdiction) {
        taxpayerClient.getTaxpayer(bearerToken, taxpayerId);
        String sk = taxYear + "#" + formType.toUpperCase() + "#" + jurisdiction.toUpperCase();
        return filingRepo.findById(taxpayerId.toString(), sk)
            .map(predictionService::backfillIfMissing)
            .map(filingMapper::toResponse)
            .orElseThrow(() -> TaxRefundException.notFound("Filing not found"));
    }
}
