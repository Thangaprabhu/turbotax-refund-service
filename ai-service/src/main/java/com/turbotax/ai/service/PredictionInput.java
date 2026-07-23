package com.turbotax.ai.prediction;

import com.turbotax.ai.domain.enums.FormType;
import com.turbotax.ai.domain.enums.IrsStatus;

public record PredictionInput(FormType formType, String jurisdiction, IrsStatus irsStatus) {}
