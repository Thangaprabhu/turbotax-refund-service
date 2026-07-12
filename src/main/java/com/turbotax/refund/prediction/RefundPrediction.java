package com.turbotax.refund.prediction;

public record RefundPrediction(Integer predictedDays, Double confidence, String modelVersion) {}
