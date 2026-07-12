package com.turbotax.refund.prediction;

import com.turbotax.refund.domain.enums.FormType;
import com.turbotax.refund.domain.enums.IrsStatus;

public record PredictionInput(FormType formType, String jurisdiction, IrsStatus irsStatus) {}
