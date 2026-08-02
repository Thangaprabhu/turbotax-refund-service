package com.turbotax.refund.domain.dto.response;

import com.turbotax.refund.dynamodb.FilingItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record FilingResponse(
    @Schema(example = "b1a45447-b096-45f4-bb28-02a9d33a7cc4") String taxpayerId,
    @Schema(example = "2025#F1040#FEDERAL") String sk,
    @Schema(example = "2025") String taxYear,
    @Schema(example = "F1040") String formType,
    @Schema(example = "FEDERAL") String jurisdiction,
    @Schema(example = "UNDER_REVIEW") String irsStatus,
    @Schema(example = "2026-02-01") String filingDate,
    @Schema(example = "2026-03-15") String expectedDepositDate,
    @Schema(example = "51") Integer aiPredictedDays,
    @Schema(example = "0.35") Double aiConfidence,
    @Schema(example = "2026-07-12T16:15:18.549030Z") String lastSyncedAt,
    List<FilingItem.StatusHistoryEntry> statusHistory
) {}
