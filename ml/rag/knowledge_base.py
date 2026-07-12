"""
Source-of-truth content for the refund-issue guidance RAG knowledge base.
Every DOCS entry is a short, factual, publicly-known statement about IRS/state
refund processing -- deliberately conservative (no invented dates, amounts,
or promises) since this content is what a future LLM synthesis step would be
grounded in.

entity_types / jurisdictions are a structured pre-filter, applied before
ranking by similarity: with only ~18 short docs and a small local embedding
model, cosine similarity alone let off-topic content (e.g. business-only ERC
delays) leak into individual-federal results. "ANY" means the doc applies
regardless of that dimension.

SITUATIONS is the finite, enumerable set of (status x entity type x
jurisdiction) combinations the app needs guidance for.
"""

DOCS = [
    {
        "topic": "identity_verification",
        "content": (
            "The IRS may flag a return for identity verification if it can't confirm the filer's "
            "identity from the information submitted. This is a common, routine anti-fraud check, "
            "not an accusation of wrongdoing. The IRS typically mails a notice (often a 5071C or "
            "4883C letter) with instructions to verify identity online or by phone before "
            "processing resumes."
        ),
        "source_url": "https://www.irs.gov/identity-theft-fraud-scams/identity-and-tax-return-verification-service",
        "entity_types": ["ANY"],
        "jurisdictions": ["FEDERAL"],
    },
    {
        "topic": "path_act_eitc_actc",
        "content": (
            "Under the PATH Act, the IRS is legally required to hold the entire refund -- not just "
            "the credit portion -- for any return claiming the Earned Income Tax Credit (EITC) or "
            "Additional Child Tax Credit (ACTC) until at least mid-to-late February, regardless of "
            "how early the return was filed or how quickly it was otherwise processed."
        ),
        "source_url": "https://www.irs.gov/refunds/refund-timing-for-earned-income-tax-credit-and-additional-child-tax-credit-filers",
        "entity_types": ["INDIVIDUAL"],
        "jurisdictions": ["FEDERAL"],
    },
    {
        "topic": "under_review_general",
        "content": (
            "A return marked as under review is being examined more closely before a refund is "
            "released. Common triggers include claimed credits that need verification, income "
            "that doesn't match third-party reporting (like W-2s or 1099s), or random compliance "
            "sampling. Most reviews resolve automatically and don't require the filer to do "
            "anything unless the IRS sends a specific notice requesting information."
        ),
        "source_url": "https://www.irs.gov/refunds",
        "entity_types": ["ANY"],
        "jurisdictions": ["ANY"],
    },
    {
        "topic": "wheres_my_refund_tool",
        "content": (
            "The IRS 'Where's My Refund' tool (and the IRS2Go app) shows the most current status "
            "using the filer's SSN/EIN, filing status, and exact refund amount. It updates once "
            "every 24 hours, usually overnight, so checking more often than daily won't reveal new "
            "information."
        ),
        "source_url": "https://www.irs.gov/wheres-my-refund",
        "entity_types": ["ANY"],
        "jurisdictions": ["FEDERAL"],
    },
    {
        "topic": "irs_phone_contact",
        "content": (
            "The IRS refund hotline is 800-829-1954 for individual returns. The IRS generally asks "
            "filers not to call unless it has been more than 21 days since e-filing (or 6 weeks "
            "for a paper return) or the Where's My Refund tool specifically directs them to call, "
            "since phone representatives typically can't access more detail than the online tool."
        ),
        "source_url": "https://www.irs.gov/refunds",
        "entity_types": ["ANY"],
        "jurisdictions": ["FEDERAL"],
    },
    {
        "topic": "business_return_review",
        "content": (
            "Business returns (corporate, partnership, and payroll/employment tax forms) don't "
            "have a published cycle chart like individual e-filed returns do, and commonly take "
            "significantly longer -- often six to twelve weeks or more -- because they're more "
            "likely to require manual review of credits, elections, or multi-year adjustments."
        ),
        "source_url": "https://www.irs.gov/businesses",
        "entity_types": ["BUSINESS"],
        "jurisdictions": ["ANY"],
    },
    {
        "topic": "state_refund_delays",
        "content": (
            "Each state department of revenue sets and publishes its own refund timeline "
            "independent of the IRS; there is no single unified schedule across states. State "
            "refunds are generally slower and more variable than federal e-filed refunds, and "
            "filers should check their specific state's refund-status tool rather than assume "
            "federal timing applies."
        ),
        "source_url": "https://www.irs.gov/filing/wheres-my-state-refund",
        "entity_types": ["ANY"],
        "jurisdictions": ["STATE"],
    },
    {
        "topic": "state_review_process",
        "content": (
            "State departments of revenue run their own income and fraud-prevention checks "
            "independent of the IRS, commonly cross-checking reported wages against employer "
            "filings before releasing a refund. A state review does not necessarily mean the IRS "
            "found anything wrong with the federal return."
        ),
        "source_url": "https://www.irs.gov/filing/wheres-my-state-refund",
        "entity_types": ["ANY"],
        "jurisdictions": ["STATE"],
    },
    {
        "topic": "state_identity_verification",
        "content": (
            "Many states run their own identity-verification step before releasing a refund, "
            "separate from any IRS process. A state may mail its own letter or require the filer "
            "to verify identity through the state's own online portal rather than the IRS's."
        ),
        "source_url": "https://www.irs.gov/filing/wheres-my-state-refund",
        "entity_types": ["ANY"],
        "jurisdictions": ["STATE"],
    },
    {
        "topic": "math_error_notice",
        "content": (
            "If the IRS finds a discrepancy (like a credit claimed incorrectly or a data-entry "
            "mismatch), it may issue a math error notice, adjust the refund amount, and continue "
            "processing without a full audit. The notice explains the change and gives the filer "
            "60 days to dispute it if they believe the IRS is wrong."
        ),
        "source_url": "https://www.irs.gov/newsroom/understanding-your-cp12-notice",
        "entity_types": ["ANY"],
        "jurisdictions": ["FEDERAL"],
    },
    {
        "topic": "refund_offset",
        "content": (
            "The Treasury Offset Program can apply some or all of a refund to certain past-due "
            "debts, such as federal or state tax debt, defaulted student loans, or overdue child "
            "support, before the remainder (if any) is sent to the filer. The Bureau of the Fiscal "
            "Service, not the IRS, sends a separate notice explaining any offset."
        ),
        "source_url": "https://www.irs.gov/taxtopics/tc203",
        "entity_types": ["ANY"],
        "jurisdictions": ["ANY"],
    },
    {
        "topic": "amended_return",
        "content": (
            "Amended returns (Form 1040-X) are processed separately from original returns and "
            "take substantially longer -- the IRS states up to 16 weeks or more -- and can only be "
            "tracked with the separate 'Where's My Amended Return' tool, not the standard refund "
            "tracker."
        ),
        "source_url": "https://www.irs.gov/filing/wheres-my-amended-return",
        "entity_types": ["INDIVIDUAL"],
        "jurisdictions": ["FEDERAL"],
    },
    {
        "topic": "disbursement_method",
        "content": (
            "How a refund is disbursed affects the final leg of the timeline: direct deposit via "
            "ACH typically lands within a few days of the IRS releasing the refund, while a mailed "
            "paper check can add one to several additional weeks depending on postal delivery."
        ),
        "source_url": "https://www.irs.gov/refunds",
        "entity_types": ["ANY"],
        "jurisdictions": ["ANY"],
    },
    {
        "topic": "erc_941_delays",
        "content": (
            "Refunds tied to employment tax adjustments, including Employee Retention Credit (ERC) "
            "claims on Form 941-X, have been publicly documented by the IRS as taking many months "
            "due to heightened fraud screening on this specific program, well beyond typical "
            "business-return timelines."
        ),
        "source_url": "https://www.irs.gov/coronavirus/employee-retention-credit",
        "entity_types": ["BUSINESS"],
        "jurisdictions": ["FEDERAL"],
    },
    {
        "topic": "bank_account_issues",
        "content": (
            "If a direct deposit is rejected -- for example due to a closed account or a mismatched "
            "name -- the bank returns the funds to the IRS, which then reissues the refund as a "
            "mailed paper check, adding delay beyond the original estimate."
        ),
        "source_url": "https://www.irs.gov/refunds",
        "entity_types": ["ANY"],
        "jurisdictions": ["ANY"],
    },
    {
        "topic": "tax_transcript",
        "content": (
            "Filers who want more detail than a status label can request a free account or return "
            "transcript from the IRS, which shows processing codes and dates that sometimes surface "
            "issues (like a hold or offset) before a formal notice arrives in the mail."
        ),
        "source_url": "https://www.irs.gov/individuals/get-transcript",
        "entity_types": ["ANY"],
        "jurisdictions": ["FEDERAL"],
    },
    {
        "topic": "cp05_notice",
        "content": (
            "A CP05 notice means the IRS is verifying income, withholding, or credits claimed "
            "before releasing a refund and is not, by itself, a request for the filer to do "
            "anything -- it's informational. If the IRS needs documentation, it follows up with a "
            "separate, more specific notice (such as a CP05A)."
        ),
        "source_url": "https://www.irs.gov/individuals/understanding-your-cp05-notice",
        "entity_types": ["INDIVIDUAL"],
        "jurisdictions": ["FEDERAL"],
    },
    {
        "topic": "injured_spouse",
        "content": (
            "On a joint return, if only one spouse owes the debt behind a refund offset, the other "
            "spouse can file Form 8379 (Injured Spouse Allocation) to claim their share of the "
            "refund back; this can be filed with the original return or afterward, but adds "
            "processing time either way."
        ),
        "source_url": "https://www.irs.gov/forms-pubs/about-form-8379",
        "entity_types": ["INDIVIDUAL"],
        "jurisdictions": ["FEDERAL"],
    },
]

# The finite set of situations the app needs guidance for today -- only
# FLAGGED and UNDER_REVIEW surface a guidance card (see ActionGuidanceCard.tsx).
# "description" is the retrieval query text embedded at ingestion time.
SITUATIONS = [
    {
        "situation_key": "FLAGGED_INDIVIDUAL_FEDERAL",
        "entity_type": "INDIVIDUAL",
        "jurisdiction": "FEDERAL",
        "description": "An individual's federal tax return has been flagged by the IRS for additional verification, most likely identity verification.",
    },
    {
        "situation_key": "FLAGGED_INDIVIDUAL_STATE",
        "entity_type": "INDIVIDUAL",
        "jurisdiction": "STATE",
        "description": "An individual's state tax refund has been flagged for review by the state tax agency.",
    },
    {
        "situation_key": "FLAGGED_BUSINESS_FEDERAL",
        "entity_type": "BUSINESS",
        "jurisdiction": "FEDERAL",
        "description": "A business federal tax return, such as a corporate, partnership, or payroll tax filing, has been flagged by the IRS for additional review.",
    },
    {
        "situation_key": "FLAGGED_BUSINESS_STATE",
        "entity_type": "BUSINESS",
        "jurisdiction": "STATE",
        "description": "A business state tax refund has been flagged for review by the state tax agency.",
    },
    {
        "situation_key": "UNDER_REVIEW_INDIVIDUAL_FEDERAL",
        "entity_type": "INDIVIDUAL",
        "jurisdiction": "FEDERAL",
        "description": "An individual's federal tax return is under manual IRS review, potentially due to claiming the Earned Income Tax Credit or Additional Child Tax Credit, income mismatches, or random selection.",
    },
    {
        "situation_key": "UNDER_REVIEW_INDIVIDUAL_STATE",
        "entity_type": "INDIVIDUAL",
        "jurisdiction": "STATE",
        "description": "An individual's state tax refund is taking longer than expected and is under review by the state tax agency.",
    },
    {
        "situation_key": "UNDER_REVIEW_BUSINESS_FEDERAL",
        "entity_type": "BUSINESS",
        "jurisdiction": "FEDERAL",
        "description": "A business federal tax return, potentially including an Employee Retention Credit claim on employment tax forms, is under extended IRS review.",
    },
    {
        "situation_key": "UNDER_REVIEW_BUSINESS_STATE",
        "entity_type": "BUSINESS",
        "jurisdiction": "STATE",
        "description": "A business state tax refund is under extended review by the state tax agency.",
    },
]
