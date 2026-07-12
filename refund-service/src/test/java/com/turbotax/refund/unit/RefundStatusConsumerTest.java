package com.turbotax.refund.unit;

import com.turbotax.refund.domain.event.RefundStatusUpdatedEvent;
import com.turbotax.refund.kafka.consumer.RefundStatusConsumer;
import com.turbotax.refund.metrics.TaxMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefundStatusConsumerTest {

    @Mock TaxMetrics taxMetrics;

    RefundStatusConsumer consumer;

    @org.junit.jupiter.api.BeforeEach
    void setup() {
        consumer = new RefundStatusConsumer(taxMetrics);
    }

    @Test
    void onRefundStatusUpdated_shouldIncrementFederalMetric_forFederalJurisdiction() {
        var event = new RefundStatusUpdatedEvent("tp-1", "2024", "F1040", "FEDERAL", "RECEIVED", "APPROVED", "2024-01-01T00:00:00Z");

        consumer.onRefundStatusUpdated(event, 0, 100L);

        verify(taxMetrics).incrementFederalReturnStatusUpdated();
        verify(taxMetrics, never()).incrementStateReturnStatusUpdated();
    }

    @Test
    void onRefundStatusUpdated_shouldIncrementStateMetric_forStateJurisdiction() {
        var event = new RefundStatusUpdatedEvent("tp-1", "2024", "F1040", "CA", "RECEIVED", "APPROVED", "2024-01-01T00:00:00Z");

        consumer.onRefundStatusUpdated(event, 1, 200L);

        verify(taxMetrics).incrementStateReturnStatusUpdated();
        verify(taxMetrics, never()).incrementFederalReturnStatusUpdated();
    }

    @Test
    void onRefundStatusUpdated_shouldTreatJurisdictionCaseInsensitively() {
        var event = new RefundStatusUpdatedEvent("tp-1", "2024", "F1040", "federal", "RECEIVED", "APPROVED", "2024-01-01T00:00:00Z");

        consumer.onRefundStatusUpdated(event, 0, 1L);

        verify(taxMetrics).incrementFederalReturnStatusUpdated();
    }
}
