package com.turbotax.refund.domain.dto.response;

import com.turbotax.refund.dynamodb.FilingItem;

import java.util.List;

public record FilingResponse(
    String taxpayerId,
    String sk,
    String taxYear,
    String formType,
    String jurisdiction,
    String irsStatus,
    String filingDate,
    String expectedDepositDate,
    Integer aiPredictedDays,
    Double aiConfidence,
    String lastSyncedAt,
    List<FilingItem.StatusHistoryEntry> statusHistory
) {}
