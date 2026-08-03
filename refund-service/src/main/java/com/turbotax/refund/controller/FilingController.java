package com.turbotax.refund.controller;

import com.turbotax.refund.domain.dto.request.CreateFilingRequest;
import com.turbotax.refund.domain.dto.response.FilingResponse;
import com.turbotax.refund.domain.dto.response.PageResponse;
import com.turbotax.refund.service.FilingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/taxpayers/{taxpayerId}/filings")
@RequiredArgsConstructor
@Tag(name = "Filings", description = "Tax filing and refund status")
@SecurityRequirement(name = "bearerAuth")
public class FilingController {

    private final FilingService filingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new filing (triggers IRS polling)")
    public FilingResponse create(@RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
                                  @PathVariable UUID taxpayerId, @Valid @RequestBody CreateFilingRequest request) {
        return filingService.create(bearerToken, taxpayerId, request);
    }

    @GetMapping
    @Operation(summary = "List all filings for a taxpayer (paginated)")
    public PageResponse<FilingResponse> listAll(@RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
                                                 @PathVariable UUID taxpayerId,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        return filingService.findAllPaginated(bearerToken, taxpayerId, page, size);
    }

    @GetMapping("/latest")
    @Operation(summary = "Get most recent filing + refund status")
    public FilingResponse getLatest(@RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken, @PathVariable UUID taxpayerId) {
        return filingService.findLatest(bearerToken, taxpayerId);
    }

    @GetMapping("/{taxYear}/{formType}/{jurisdiction}")
    @Operation(summary = "Get refund status for specific year/form/jurisdiction")
    public FilingResponse getByYear(@RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken, @PathVariable UUID taxpayerId,
                                     @PathVariable String taxYear, @PathVariable String formType, @PathVariable String jurisdiction) {
        return filingService.findByYear(bearerToken, taxpayerId, taxYear, formType, jurisdiction);
    }
}
