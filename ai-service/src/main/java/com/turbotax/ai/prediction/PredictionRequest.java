package com.turbotax.ai.prediction;

import com.turbotax.ai.domain.enums.FormType;
import com.turbotax.ai.domain.enums.IrsStatus;

public record PredictionRequest(FormType formType, String jurisdiction, IrsStatus irsStatus) {}
