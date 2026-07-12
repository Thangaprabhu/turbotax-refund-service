package com.turbotax.refund.domain.event;

public record RefundStatusUpdatedEvent(
    String taxpayerId,
    String taxYear,
    String formType,
    String jurisdiction,
    String oldStatus,
    String newStatus,
    String occurredAt
) {}
