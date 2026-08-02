package com.turbotax.refund.client;

import io.swagger.v3.oas.annotations.media.Schema;

public record GuidanceDoc(
    @Schema(example = "1") long id,
    @Schema(example = "identity_verification") String topic,
    @Schema(example = "The IRS may flag a return for identity verification if it can't confirm the filer's identity from the information submitted. This is a common, routine anti-fraud check, not an accusation of wrongdoing.") String content,
    @Schema(example = "https://www.irs.gov/identity-theft-fraud-scams/identity-and-tax-return-verification-service") String sourceUrl,
    @Schema(example = "false", description = "True for demo-authored support-playbook/FAQ content with no real source_url -- never actual IRS/state guidance.")
    boolean simulated
) {}
