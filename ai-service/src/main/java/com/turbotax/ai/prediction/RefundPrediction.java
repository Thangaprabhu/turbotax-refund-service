package com.turbotax.ai.prediction;

public record RefundPrediction(Integer predictedDays, Double confidence, String modelVersion) {}
