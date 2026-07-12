package com.turbotax.ai.controller;

import com.turbotax.ai.domain.enums.FormType;
import com.turbotax.ai.domain.enums.IrsStatus;
import com.turbotax.ai.guidance.GuidanceResponse;
import com.turbotax.ai.guidance.RefundGuidanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/guidance")
@RequiredArgsConstructor
public class GuidanceController {

    private final RefundGuidanceService refundGuidanceService;

    @GetMapping
    public ResponseEntity<GuidanceResponse> getGuidance(@RequestParam FormType formType,
                                                          @RequestParam String jurisdiction,
                                                          @RequestParam IrsStatus irsStatus) {
        return refundGuidanceService.getGuidance(formType, jurisdiction, irsStatus)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
