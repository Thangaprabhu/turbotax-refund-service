package com.turbotax.refund.service;

import com.turbotax.refund.client.TaxpayerClient;
import com.turbotax.refund.domain.dto.response.FilingResponse;
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
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatusUpdateService {

    private final FilingDynamoRepository filingRepo;
    private final TaxpayerClient taxpayerClient;
    private final FilingEventProducer eventProducer;
    private final TaxMetrics taxMetrics;
    private final FilingMapper filingMapper;
    private final FilingPredictionService predictionService;

    @CacheEvict(value = "filings", key = "#taxpayerId")
    public FilingResponse updateStatus(String bearerToken, UUID taxpayerId, String sk, IrsStatus newStatus) {
        taxpayerClient.getTaxpayer(bearerToken, taxpayerId);

        FilingItem item = filingRepo.findById(taxpayerId.toString(), sk)
            .orElseThrow(() -> TaxRefundException.notFound("Filing not found"));

        String oldStatus = item.getIrsStatus();
        item.setIrsStatus(newStatus.name());
        item.setLastSyncedAt(Instant.now().toString());
        predictionService.applyPrediction(item);
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
}
