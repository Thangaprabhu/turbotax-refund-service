package com.turbotax.refund.kafka.consumer;

import com.turbotax.refund.domain.event.RefundStatusUpdatedEvent;
import com.turbotax.refund.metrics.TaxMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RefundStatusConsumer {

    private final TaxMetrics taxMetrics;

    @KafkaListener(
        topics = "${app.kafka.topics.refund-status-updated:refund.status.updated}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onRefundStatusUpdated(
        @Payload RefundStatusUpdatedEvent event,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info("Consumed RefundStatusUpdatedEvent: taxpayer={} {} -> {} partition={} offset={}",
            event.taxpayerId(), event.oldStatus(), event.newStatus(), partition, offset);

        if ("FEDERAL".equalsIgnoreCase(event.jurisdiction())) {
            taxMetrics.incrementFederalReturnStatusUpdated();
        } else {
            taxMetrics.incrementStateReturnStatusUpdated();
        }
    }
}
