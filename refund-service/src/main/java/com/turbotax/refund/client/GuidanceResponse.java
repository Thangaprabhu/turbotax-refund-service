package com.turbotax.refund.client;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record GuidanceResponse(
    @Schema(example = "UNDER_REVIEW_INDIVIDUAL_FEDERAL") String situationKey,
    @Schema(example = "A return marked as under review is being examined more closely before a refund is released. Common triggers include claimed credits that need verification, income that doesn't match third-party reporting, or random compliance sampling.") String narrative,
    List<GuidanceDoc> sources
) {}
