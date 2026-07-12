package com.turbotax.refund.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TaxMetrics {

    private final MeterRegistry registry;
    private final Counter userSubmittedTaxCount;
    private final Counter expectedFederalReturnCount;
    private final Counter expectedStateReturnCount;
    private final Counter federalReturnStatusUpdatedCount;
    private final Counter stateReturnStatusUpdatedCount;

    public TaxMetrics(MeterRegistry registry) {
        this.registry = registry;
        userSubmittedTaxCount = Counter.builder("user_submitted_tax_count")
            .description("Number of tax returns submitted by users")
            .register(registry);

        expectedFederalReturnCount = Counter.builder("expected_federal_return_count")
            .description("Number of expected federal refunds")
            .register(registry);

        expectedStateReturnCount = Counter.builder("expected_state_return_count")
            .description("Number of expected state refunds")
            .register(registry);

        federalReturnStatusUpdatedCount = Counter.builder("federal_return_status_updated_count")
            .description("Number of federal return status updates received from IRS")
            .register(registry);

        stateReturnStatusUpdatedCount = Counter.builder("state_return_status_updated_count")
            .description("Number of state return status updates received")
            .register(registry);
    }

    public void incrementUserSubmittedTax() {
        userSubmittedTaxCount.increment();
    }

    public void incrementExpectedFederalReturn() {
        expectedFederalReturnCount.increment();
    }

    public void incrementExpectedStateReturn() {
        expectedStateReturnCount.increment();
    }

    public void incrementFederalReturnStatusUpdated() {
        federalReturnStatusUpdatedCount.increment();
    }

    public void incrementStateReturnStatusUpdated() {
        stateReturnStatusUpdatedCount.increment();
    }

    public void incrementRefundPredictionGenerated(String modelVersion) {
        Counter.builder("refund_prediction_generated_count")
            .description("Number of refund-timing predictions generated, tagged by engine version")
            .tag("model_version", modelVersion)
            .register(registry)
            .increment();
    }
}
