package com.turbotax.refund.controller;

import com.turbotax.refund.domain.dto.request.CreateFilingRequest;
import com.turbotax.refund.domain.dto.request.UpdateFilingStatusRequest;
import com.turbotax.refund.domain.enums.FormType;
import com.turbotax.refund.domain.enums.IrsStatus;
import com.turbotax.refund.domain.dto.response.FilingResponse;
import com.turbotax.refund.domain.dto.response.PageResponse;
import com.turbotax.refund.guidance.GuidanceResponse;
import com.turbotax.refund.guidance.RefundGuidanceService;
import com.turbotax.refund.service.FilingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/taxpayers/{taxpayerId}/filings")
@RequiredArgsConstructor
@Tag(name = "Filings", description = "Tax filing and refund status")
@SecurityRequirement(name = "bearerAuth")
public class FilingController {

    private final FilingService filingService;
    private final RefundGuidanceService refundGuidanceService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new filing (triggers IRS polling)")
    public FilingResponse create(@AuthenticationPrincipal String userId, @PathVariable UUID taxpayerId, @Valid @RequestBody CreateFilingRequest request) {
        return filingService.create(UUID.fromString(userId), taxpayerId, request);
    }

    @GetMapping
    @Operation(summary = "List all filings for a taxpayer (paginated)")
    public PageResponse<FilingResponse> listAll(@AuthenticationPrincipal String userId, @PathVariable UUID taxpayerId,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        return filingService.findAllPaginated(UUID.fromString(userId), taxpayerId, page, size);
    }

    @GetMapping("/latest")
    @Operation(summary = "Get most recent filing + refund status")
    public FilingResponse getLatest(@AuthenticationPrincipal String userId, @PathVariable UUID taxpayerId) {
        return filingService.findLatest(UUID.fromString(userId), taxpayerId);
    }

    @GetMapping("/{taxYear}/{formType}/{jurisdiction}")
    @Operation(summary = "Get refund status for specific year/form/jurisdiction")
    public FilingResponse getByYear(@AuthenticationPrincipal String userId, @PathVariable UUID taxpayerId, @PathVariable String taxYear, @PathVariable String formType, @PathVariable String jurisdiction) {
        return filingService.findByYear(UUID.fromString(userId), taxpayerId, taxYear, formType, jurisdiction);
    }

    @GetMapping("/{taxYear}/{formType}/{jurisdiction}/guidance")
    @Operation(summary = "Get RAG-retrieved guidance for a flagged or under-review filing",
        description = "Returns 204 if the filing's current status doesn't need guidance (e.g. RECEIVED, APPROVED, DEPOSITED).")
    public ResponseEntity<GuidanceResponse> getGuidance(@AuthenticationPrincipal String userId, @PathVariable UUID taxpayerId,
                                                         @PathVariable String taxYear, @PathVariable String formType, @PathVariable String jurisdiction) {
        FilingResponse filing = filingService.findByYear(UUID.fromString(userId), taxpayerId, taxYear, formType, jurisdiction);
        return refundGuidanceService.getGuidance(FormType.valueOf(filing.formType()), filing.jurisdiction(), IrsStatus.valueOf(filing.irsStatus()))
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PatchMapping("/{sk}/status")
    @Operation(summary = "Update IRS refund status for a filing", description = "sk format: {taxYear}#{formType}#{jurisdiction} e.g. 2024#F1040#FEDERAL")
    public FilingResponse updateStatus(@AuthenticationPrincipal String userId, @PathVariable UUID taxpayerId, @PathVariable String sk, @Valid @RequestBody UpdateFilingStatusRequest request) {
        return filingService.updateStatus(UUID.fromString(userId), taxpayerId, sk, request.irsStatus());
    }
}
