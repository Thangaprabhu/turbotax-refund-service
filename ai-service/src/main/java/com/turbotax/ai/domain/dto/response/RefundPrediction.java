package com.turbotax.ai.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record RefundPrediction(
    @Schema(example = "51") Integer predictedDays,
    @Schema(example = "0.35") Double confidence,
    @Schema(example = "rules-v1") String modelVersion
) {}
