package com.turbotax.refund.client;

import com.turbotax.refund.domain.enums.FormType;
import com.turbotax.refund.domain.enums.IrsStatus;

public record PredictionRequest(FormType formType, String jurisdiction, IrsStatus irsStatus) {}
