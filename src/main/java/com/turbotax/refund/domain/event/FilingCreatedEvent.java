package com.turbotax.refund.domain.event;

import com.turbotax.refund.domain.enums.TaxpayerType;
import com.turbotax.refund.domain.enums.FormType;

public record FilingCreatedEvent(
    String taxpayerId,
    TaxpayerType taxpayerType,
    FormType formType,
    String taxYear,
    String jurisdiction,
    String occurredAt
) {}
