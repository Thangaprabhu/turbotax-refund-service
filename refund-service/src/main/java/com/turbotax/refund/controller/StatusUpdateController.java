package com.turbotax.refund.controller;

import com.turbotax.refund.domain.dto.request.UpdateFilingStatusRequest;
import com.turbotax.refund.domain.dto.response.FilingResponse;
import com.turbotax.refund.service.StatusUpdateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/taxpayers/{taxpayerId}/filings")
@RequiredArgsConstructor
@Tag(name = "Filings", description = "Tax filing and refund status")
@SecurityRequirement(name = "bearerAuth")
public class StatusUpdateController {

    private final StatusUpdateService statusUpdateService;

    @PatchMapping("/{sk}/status")
    @Operation(summary = "Update IRS refund status for a filing", description = "sk format: {taxYear}#{formType}#{jurisdiction} e.g. 2024#F1040#FEDERAL")
    public FilingResponse updateStatus(@RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken, @PathVariable UUID taxpayerId,
                                        @PathVariable String sk, @Valid @RequestBody UpdateFilingStatusRequest request) {
        return statusUpdateService.updateStatus(bearerToken, taxpayerId, sk, request.irsStatus());
    }
}
