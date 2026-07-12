package com.turbotax.refund.unit;

import com.turbotax.refund.metrics.TaxMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaxMetricsTest {

    SimpleMeterRegistry registry;
    TaxMetrics taxMetrics;

    @BeforeEach
    void setup() {
        registry = new SimpleMeterRegistry();
        taxMetrics = new TaxMetrics(registry);
    }

    @Test
    void incrementUserSubmittedTax_shouldIncreaseCounter() {
        taxMetrics.incrementUserSubmittedTax();
        taxMetrics.incrementUserSubmittedTax();

        assertThat(registry.get("user_submitted_tax_count").counter().count()).isEqualTo(2.0);
    }

    @Test
    void incrementExpectedFederalReturn_shouldIncreaseCounter() {
        taxMetrics.incrementExpectedFederalReturn();

        assertThat(registry.get("expected_federal_return_count").counter().count()).isEqualTo(1.0);
    }

    @Test
    void incrementExpectedStateReturn_shouldIncreaseCounter() {
        taxMetrics.incrementExpectedStateReturn();

        assertThat(registry.get("expected_state_return_count").counter().count()).isEqualTo(1.0);
    }

    @Test
    void incrementFederalReturnStatusUpdated_shouldIncreaseCounter() {
        taxMetrics.incrementFederalReturnStatusUpdated();

        assertThat(registry.get("federal_return_status_updated_count").counter().count()).isEqualTo(1.0);
    }

    @Test
    void incrementStateReturnStatusUpdated_shouldIncreaseCounter() {
        taxMetrics.incrementStateReturnStatusUpdated();

        assertThat(registry.get("state_return_status_updated_count").counter().count()).isEqualTo(1.0);
    }

    @Test
    void incrementRefundPredictionGenerated_shouldTagByModelVersion() {
        taxMetrics.incrementRefundPredictionGenerated("rules-v1");
        taxMetrics.incrementRefundPredictionGenerated("rules-v1");
        taxMetrics.incrementRefundPredictionGenerated("survival-v1");

        assertThat(registry.get("refund_prediction_generated_count").tag("model_version", "rules-v1").counter().count())
            .isEqualTo(2.0);
        assertThat(registry.get("refund_prediction_generated_count").tag("model_version", "survival-v1").counter().count())
            .isEqualTo(1.0);
    }
}
