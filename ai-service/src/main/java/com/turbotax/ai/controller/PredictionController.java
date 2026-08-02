package com.turbotax.ai.controller;

import com.turbotax.ai.domain.dto.response.RefundPrediction;
import com.turbotax.ai.domain.enums.FormType;
import com.turbotax.ai.domain.enums.IrsStatus;
import com.turbotax.ai.service.PredictionInput;
import com.turbotax.ai.service.RefundPredictor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/predictions")
@RequiredArgsConstructor
public class PredictionController {

    private final RefundPredictor refundPredictor;

    @GetMapping
    public ResponseEntity<RefundPrediction> predict(@RequestParam FormType formType,
                                                      @RequestParam String jurisdiction,
                                                      @RequestParam IrsStatus irsStatus) {
        var input = new PredictionInput(formType, jurisdiction, irsStatus);
        return refundPredictor.predict(input)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
