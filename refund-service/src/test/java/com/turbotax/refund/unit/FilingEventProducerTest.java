package com.turbotax.refund.unit;

import com.turbotax.refund.domain.enums.FormType;
import com.turbotax.refund.domain.enums.TaxpayerType;
import com.turbotax.refund.domain.event.FilingCreatedEvent;
import com.turbotax.refund.domain.event.RefundStatusUpdatedEvent;
import com.turbotax.refund.kafka.producer.FilingEventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FilingEventProducerTest {

    @Mock KafkaTemplate<String, Object> kafkaTemplate;
    @SuppressWarnings("unchecked")
    SendResult<String, Object> sendResult = mock(SendResult.class);

    FilingEventProducer producer;

    @BeforeEach
    void setup() {
        producer = new FilingEventProducer(kafkaTemplate);
        ReflectionTestUtils.setField(producer, "filingCreatedTopic", "filing.created");
        ReflectionTestUtils.setField(producer, "refundStatusUpdatedTopic", "refund.status.updated");
    }

    @Test
    void publishFilingCreated_shouldSendToFilingCreatedTopic_onSuccess() {
        var taxpayerId = UUID.randomUUID();
        RecordMetadata metadata = mock(RecordMetadata.class);
        when(metadata.partition()).thenReturn(0);
        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        when(kafkaTemplate.send(eq("filing.created"), eq(taxpayerId.toString()), any(FilingCreatedEvent.class)))
            .thenReturn(CompletableFuture.completedFuture(sendResult));

        producer.publishFilingCreated(taxpayerId, TaxpayerType.INDIVIDUAL, FormType.F1040, "2024", "FEDERAL");

        verify(kafkaTemplate).send(eq("filing.created"), eq(taxpayerId.toString()), any(FilingCreatedEvent.class));
    }

    @Test
    void publishFilingCreated_shouldLogButNotThrow_whenSendFails() {
        var taxpayerId = UUID.randomUUID();
        var failed = new CompletableFuture<SendResult<String, Object>>();
        failed.completeExceptionally(new RuntimeException("kafka down"));
        when(kafkaTemplate.send(eq("filing.created"), eq(taxpayerId.toString()), any(FilingCreatedEvent.class)))
            .thenReturn(failed);

        producer.publishFilingCreated(taxpayerId, TaxpayerType.BUSINESS, FormType.F1120, "2024", "FEDERAL");

        verify(kafkaTemplate).send(eq("filing.created"), eq(taxpayerId.toString()), any(FilingCreatedEvent.class));
    }

    @Test
    void publishStatusUpdated_shouldSendToRefundStatusTopic_onSuccess() {
        when(kafkaTemplate.send(eq("refund.status.updated"), eq("tp-1"), any(RefundStatusUpdatedEvent.class)))
            .thenReturn(CompletableFuture.completedFuture(sendResult));

        producer.publishStatusUpdated("tp-1", "2024", "F1040", "FEDERAL", "RECEIVED", "APPROVED");

        verify(kafkaTemplate).send(eq("refund.status.updated"), eq("tp-1"), any(RefundStatusUpdatedEvent.class));
    }

    @Test
    void publishStatusUpdated_shouldLogButNotThrow_whenSendFails() {
        var failed = new CompletableFuture<SendResult<String, Object>>();
        failed.completeExceptionally(new RuntimeException("kafka down"));
        when(kafkaTemplate.send(eq("refund.status.updated"), eq("tp-1"), any(RefundStatusUpdatedEvent.class)))
            .thenReturn(failed);

        producer.publishStatusUpdated("tp-1", "2024", "F1040", "FEDERAL", "RECEIVED", "FLAGGED");

        verify(kafkaTemplate).send(eq("refund.status.updated"), eq("tp-1"), any(RefundStatusUpdatedEvent.class));
    }
}
