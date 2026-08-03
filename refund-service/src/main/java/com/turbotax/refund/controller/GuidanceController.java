package com.turbotax.refund.controller;

import com.turbotax.refund.client.GuidanceResponse;
import com.turbotax.refund.service.GuidanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/taxpayers/{taxpayerId}/filings")
@RequiredArgsConstructor
@Tag(name = "Filings", description = "Tax filing and refund status")
@SecurityRequirement(name = "bearerAuth")
public class GuidanceController {

    private final GuidanceService guidanceService;

    @GetMapping("/{taxYear}/{formType}/{jurisdiction}/guidance")
    @Operation(summary = "Get RAG-retrieved guidance for a flagged or under-review filing",
        description = "Returns 204 if the filing's current status doesn't need guidance (e.g. RECEIVED, APPROVED, DEPOSITED).")
    public ResponseEntity<GuidanceResponse> getGuidance(@RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken, @PathVariable UUID taxpayerId,
                                                         @PathVariable String taxYear, @PathVariable String formType, @PathVariable String jurisdiction) {
        return guidanceService.getGuidance(bearerToken, taxpayerId, taxYear, formType, jurisdiction)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
