package com.turbotax.refund.kafka.producer;

import com.turbotax.refund.domain.enums.TaxpayerType;
import com.turbotax.refund.domain.enums.FormType;
import com.turbotax.refund.domain.event.FilingCreatedEvent;
import com.turbotax.refund.domain.event.RefundStatusUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class FilingEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.filing-created:filing.created}")
    private String filingCreatedTopic;

    @Value("${app.kafka.topics.refund-status-updated:refund.status.updated}")
    private String refundStatusUpdatedTopic;

    public void publishFilingCreated(UUID taxpayerId, TaxpayerType taxpayerType,
                                     FormType formType, String taxYear, String jurisdiction) {
        FilingCreatedEvent event = new FilingCreatedEvent(
            taxpayerId.toString(), taxpayerType, formType,
            taxYear, jurisdiction, Instant.now().toString()
        );
        kafkaTemplate.send(filingCreatedTopic, taxpayerId.toString(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish FilingCreatedEvent for taxpayer={}", taxpayerId, ex);
                } else {
                    log.debug("Published FilingCreatedEvent: taxpayer={} partition={}",
                        taxpayerId, result.getRecordMetadata().partition());
                }
            });
    }

    public void publishStatusUpdated(String taxpayerId, String taxYear, String formType,
                                     String jurisdiction, String oldStatus, String newStatus) {
        RefundStatusUpdatedEvent event = new RefundStatusUpdatedEvent(
            taxpayerId, taxYear, formType, jurisdiction,
            oldStatus, newStatus, Instant.now().toString()
        );
        kafkaTemplate.send(refundStatusUpdatedTopic, taxpayerId, event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish RefundStatusUpdatedEvent for taxpayer={}", taxpayerId, ex);
                } else {
                    log.debug("Published RefundStatusUpdatedEvent: taxpayer={} {} -> {}",
                        taxpayerId, oldStatus, newStatus);
                }
            });
    }
}
