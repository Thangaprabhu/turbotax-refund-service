package com.turbotax.ai.controller;

import com.turbotax.ai.prediction.PredictionInput;
import com.turbotax.ai.prediction.PredictionRequest;
import com.turbotax.ai.prediction.RefundPrediction;
import com.turbotax.ai.prediction.RefundPredictor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/predictions")
@RequiredArgsConstructor
public class PredictionController {

    private final RefundPredictor refundPredictor;

    @PostMapping
    public ResponseEntity<RefundPrediction> predict(@RequestBody PredictionRequest request) {
        var input = new PredictionInput(request.formType(), request.jurisdiction(), request.irsStatus());
        return refundPredictor.predict(input)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
