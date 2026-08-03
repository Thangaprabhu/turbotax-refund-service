package com.turbotax.refund.service;

import com.turbotax.refund.client.AiClient;
import com.turbotax.refund.client.GuidanceResponse;
import com.turbotax.refund.domain.enums.FormType;
import com.turbotax.refund.domain.enums.IrsStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GuidanceService {

    private final FilingService filingService;
    private final AiClient aiClient;

    public Optional<GuidanceResponse> getGuidance(String bearerToken, UUID taxpayerId,
                                                   String taxYear, String formType, String jurisdiction) {
        var filing = filingService.findByYear(bearerToken, taxpayerId, taxYear, formType, jurisdiction);
        return aiClient.getGuidance(
            FormType.valueOf(filing.formType()), filing.jurisdiction(), IrsStatus.valueOf(filing.irsStatus()));
    }
}
