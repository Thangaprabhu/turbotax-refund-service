package com.turbotax.refund.controller;

import com.turbotax.refund.domain.dto.request.CreateTaxpayerRequest;
import com.turbotax.refund.domain.dto.response.PageResponse;
import com.turbotax.refund.domain.dto.response.TaxpayerResponse;
import com.turbotax.refund.service.TaxpayerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/taxpayers")
@RequiredArgsConstructor
@Tag(name = "Taxpayers", description = "Taxpayer management (Individual SSN / Business EIN)")
@SecurityRequirement(name = "bearerAuth")
public class TaxpayerController {

    private final TaxpayerService taxpayerService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a taxpayer (SSN or EIN)")
    public TaxpayerResponse create(@AuthenticationPrincipal String userId, @Valid @RequestBody CreateTaxpayerRequest request) {
        return taxpayerService.create(UUID.fromString(userId), request);
    }

    @GetMapping
    @Operation(summary = "List all taxpayers accessible by the logged-in user (paginated)")
    public PageResponse<TaxpayerResponse> listMine(@AuthenticationPrincipal String userId,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "10") int size) {
        return taxpayerService.findAllForUser(UUID.fromString(userId), page, size);
    }

    @GetMapping("/{taxpayerId}")
    @Operation(summary = "Get a specific taxpayer")
    public TaxpayerResponse getById(@AuthenticationPrincipal String userId, @PathVariable UUID taxpayerId) {
        return taxpayerService.findById(UUID.fromString(userId), taxpayerId);
    }
}
