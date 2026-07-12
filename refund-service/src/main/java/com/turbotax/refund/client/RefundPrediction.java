package com.turbotax.refund.client;

public record RefundPrediction(Integer predictedDays, Double confidence, String modelVersion) {}
