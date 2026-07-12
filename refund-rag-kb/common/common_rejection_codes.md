---
id: common_rejection_codes
source_type: simulated_normalized_mapping
authority: MULTI_AUTHORITY
refund_type: ANY
state_code: null
topic: COMMON_REJECTION_CODES
statuses: [REJECTED]
last_reviewed: 2026-07-08
source_url: simulated_for_demo
---

# Common Normalized Rejection Codes

This document defines demo-friendly normalized rejection codes. In production, these should map to official IRS MeF and state e-file reject codes.

## Codes

- `INVALID_TAXPAYER_ID`: Taxpayer identifier did not match authority records.
- `INVALID_DEPENDENT_ID`: Dependent identifier issue.
- `DUPLICATE_RETURN`: A return was already filed for the taxpayer and tax year.
- `MISSING_SIGNATURE`: Required signature or authorization missing.
- `BANK_ACCOUNT_INVALID`: Direct deposit account/routing information issue.
- `INCOME_MISMATCH`: Reported income differs from authority records.
- `CREDIT_REVIEW_REQUIRED`: Credit claim requires additional review.
- `IDENTITY_VERIFICATION_REQUIRED`: Tax authority requires identity verification before processing.

## User guidance

- Explain the rejection in plain language.
- Provide the next step based on the rejection code.
- Do not expose raw government codes unless useful for support.
- Route complex cases to authenticated support.
