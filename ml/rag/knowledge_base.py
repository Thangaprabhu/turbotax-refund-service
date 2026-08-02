"""
Source-of-truth content for the refund-issue guidance RAG knowledge base.
Every DOCS entry is a short, factual, publicly-known statement about IRS/state
refund processing -- deliberately conservative (no invented dates, amounts,
or promises) since this content is what a future LLM synthesis step would be
grounded in.

entity_types / jurisdictions are a structured pre-filter, applied before
ranking by similarity: with a modest number of short docs and a small local
embedding model, cosine similarity alone let off-topic content (e.g.
business-only ERC delays) leak into individual-federal results. "ANY" means
the doc applies regardless of that dimension.

jurisdictions uses "FEDERAL" or generic "STATE" to match how the app builds
a situation_key today (RefundGuidanceService#buildSituationKey collapses any
non-federal jurisdiction to "STATE" -- it doesn't yet route by specific state).
A handful of docs below are tagged with a literal state code ("CA", "NY")
instead of "STATE" -- that's deliberate: they name a specific state agency
by name (California FTB, New York DTF) and would be actively misleading if
served to a filer in a different state under the generic STATE bucket. They're
still embedded and stored for a future state-specific situation_key routing
enhancement; until that exists, they simply won't be retrieved by anything.

`simulated` marks the ~9 support-playbook/FAQ docs that are demo-authored
internal-support framing rather than an official IRS/state source (no
source_url). They're real, useful retrieval content, but the API and UI
surface the flag so a source is never confused with actual IRS/state guidance.

SITUATIONS is the finite, enumerable set of (status x entity type x
jurisdiction) combinations the app needs guidance for. Originally just
FLAGGED/UNDER_REVIEW (the only two the UI showed a guidance card for) --
now includes APPROVED and SENT too, since the corpus has real content for
"refund approved, what happens next" and "refund sent but not received"
situations and there's no reason to withhold it once it exists.
"""

DOCS = [
    {
        "topic": "identity_verification",
        "content": (
            "The IRS may flag a return for identity verification if it can't confirm the filer's "
            "identity from the information submitted. This is a common, routine anti-fraud check, "
            "not an accusation of wrongdoing. The IRS typically mails a notice (often a 5071C or "
            "4883C letter, sometimes part of the CP5071 series) with instructions to verify identity "
            "online or by phone before processing resumes. Use only the official channel named in "
            "the notice, have the notice and tax records on hand, and never share a Social Security "
            "number, bank account details, or a notice access code with a chatbot or unofficial site."
        ),
        "source_url": "https://www.irs.gov/identity-theft-fraud-scams/identity-and-tax-return-verification-service",
        "entity_types": ["ANY"],
        "jurisdictions": ["FEDERAL"],
        "simulated": False,
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
        "simulated": False,
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
        "simulated": False,
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
        "simulated": False,
    },
    {
        "topic": "wheres_my_refund_status_stages",
        "content": (
            "The IRS tracker presents three primary customer-facing stages: Return received, Refund "
            "approved, and Refund sent. Treat the tracker as the authoritative source for federal "
            "status. A status of 'Refund sent' means the IRS has released the refund -- it does not "
            "by itself confirm that a bank deposit has completed; disbursement timing depends on the "
            "delivery method."
        ),
        "source_url": "https://www.irs.gov/refunds/about-wheres-my-refund",
        "entity_types": ["ANY"],
        "jurisdictions": ["FEDERAL"],
        "simulated": False,
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
        "simulated": False,
    },
    {
        "topic": "irs_contact_routing",
        "content": (
            "For routine refund checks, the official online tracker is the right first step. Phone "
            "or refund-trace channels are appropriate only once the tracker's own published waiting "
            "criteria have been met -- calling earlier than that rarely surfaces information a phone "
            "representative can see but the tracker can't."
        ),
        "source_url": "https://www.irs.gov/faqs/irs-procedures/refund-inquiries",
        "entity_types": ["ANY"],
        "jurisdictions": ["FEDERAL"],
        "simulated": False,
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
        "simulated": False,
    },
    {
        "topic": "business_return_scope",
        "content": (
            "Business tax returns may include corporations, partnerships, exempt organizations, and "
            "other entity forms, each following its own filing and refund rules. It's important to "
            "confirm the taxpayer's actual entity type and filing form before applying guidance "
            "written for individual Form 1040 returns."
        ),
        "source_url": "https://www.irs.gov/irm/part3/irm_03-042-004r",
        "entity_types": ["BUSINESS"],
        "jurisdictions": ["FEDERAL"],
        "simulated": False,
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
        "simulated": False,
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
        "simulated": False,
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
        "simulated": False,
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
        "simulated": False,
    },
    {
        "topic": "refund_offset",
        "content": (
            "The Treasury Offset Program can apply some or all of a refund to certain past-due "
            "debts, such as federal or state tax debt, defaulted student loans, or overdue child "
            "support, before the remainder (if any) is sent to the filer. The Bureau of the Fiscal "
            "Service, not the IRS, generally sends a separate notice showing the original refund "
            "amount, the offset amount, and the agency that received it -- disputes should go to "
            "the agency named in that notice, not the IRS."
        ),
        "source_url": "https://www.irs.gov/refunds/reduced-refund",
        "entity_types": ["ANY"],
        "jurisdictions": ["ANY"],
        "simulated": False,
    },
    {
        "topic": "amended_return",
        "content": (
            "Amended returns (Form 1040-X) follow a different status workflow from original "
            "returns and take substantially longer -- the IRS states up to 16 weeks or more. Track "
            "them with the separate 'Where's My Amended Return' tool rather than the standard refund "
            "tracker, and don't apply an original return's cycle-time guidance to an amended one."
        ),
        "source_url": "https://www.irs.gov/filing/wheres-my-amended-return",
        "entity_types": ["INDIVIDUAL"],
        "jurisdictions": ["FEDERAL"],
        "simulated": False,
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
        "simulated": False,
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
        "simulated": False,
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
        "simulated": False,
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
        "simulated": False,
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
        "simulated": False,
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
        "simulated": False,
    },
    {
        "topic": "identity_theft_victim_assistance",
        "content": (
            "If a filer believes they're a victim of tax-related identity theft -- for example, "
            "someone else filed a return using their information -- the IRS's Taxpayer Protection "
            "Program may pause processing and send a verification letter. This is a distinct, more "
            "serious track from routine identity verification and should be escalated to official "
            "IRS identity-theft assistance rather than treated as a standard delay."
        ),
        "source_url": "https://www.irs.gov/individuals/how-irs-id-theft-victim-assistance-works",
        "entity_types": ["ANY"],
        "jurisdictions": ["FEDERAL"],
        "simulated": False,
    },
    {
        "topic": "identity_verification_safety_guidance",
        "content": (
            "When a return is paused for identity verification, processing typically doesn't resume "
            "until the requested verification is completed through the official channel named in the "
            "notice. Filers should have the notice and their tax records available, but should never "
            "share a Social Security number, bank account information, or a notice access code with "
            "an assistant, chatbot, or unofficial website -- only through the IRS's own verification "
            "channel."
        ),
        "source_url": "https://www.irs.gov/identity-theft-fraud-scams/verify-your-return",
        "entity_types": ["ANY"],
        "jurisdictions": ["FEDERAL"],
        "simulated": False,
    },
    {
        "topic": "common_refund_delay_reasons",
        "content": (
            "Refunds can take longer than the typical cycle for several distinct reasons: routine "
            "correction, additional review, identity verification, fraud screening, credit review, "
            "paper-return processing, amended-return processing, or a debt offset. These are "
            "different situations with different next steps -- a routine delay, an identity hold, "
            "an action-required notice, and an issued-but-not-received refund each call for "
            "different guidance rather than one generic 'it's just running long' answer."
        ),
        "source_url": "https://www.irs.gov/refunds",
        "entity_types": ["ANY"],
        "jurisdictions": ["FEDERAL"],
        "simulated": False,
    },
    {
        "topic": "manual_review_next_steps",
        "content": (
            "A return selected for manual review can take materially longer than routine processing. "
            "The recommended next steps are to check the official refund tracker and IRS online "
            "account, watch the mail for a letter, respond by any stated deadline if documentation "
            "is requested, and avoid sending unrequested documents that could themselves add delay."
        ),
        "source_url": "https://www.taxpayeradvocate.irs.gov/get-help/refunds/held-or-stopped-refunds/",
        "entity_types": ["ANY"],
        "jurisdictions": ["FEDERAL"],
        "simulated": False,
    },
    {
        "topic": "refund_sent_not_received",
        "content": (
            "When the official tracker reports a refund was sent, first confirm the disbursement "
            "method and how much time has elapsed, since direct deposit and a mailed paper check "
            "have very different normal delivery windows. A refund trace may be appropriate once "
            "the published waiting period has passed, but filing a duplicate return is never the "
            "right response to a refund that shows as sent but not yet received."
        ),
        "source_url": "https://www.taxpayeradvocate.irs.gov/get-help/refunds/i-dont-have-my-refund/",
        "entity_types": ["ANY"],
        "jurisdictions": ["FEDERAL"],
        "simulated": False,
    },
    {
        "topic": "refund_trace_form_3911",
        "content": (
            "Form 3911 (Taxpayer Statement Regarding Refund) can be used to initiate a refund trace "
            "once a refund shows as sent but hasn't arrived and the official waiting period has "
            "passed. Confirm the disbursement method first -- direct deposit and paper-check cases "
            "are handled differently -- and route joint filers through the representative-assisted "
            "process when one is required rather than filing individually."
        ),
        "source_url": "https://www.irs.gov/faqs/irs-procedures/refund-inquiries",
        "entity_types": ["ANY"],
        "jurisdictions": ["FEDERAL"],
        "simulated": False,
    },
    # -- Named-state content: tagged with a literal state code, not generic "STATE", so it
    # is stored and embedded but not yet retrievable by any situation_key. The app only
    # models a generic FEDERAL/STATE jurisdiction split today (see module docstring); serving
    # California- or New York-specific instructions to a filer in a different state would be
    # actively wrong, so these wait for a future state-specific routing enhancement.
    {
        "topic": "ca_ftb_contact_routing",
        "content": (
            "California FTB provides refund and MyFTB account support channels, separated by "
            "personal versus business guidance depending on taxpayer type."
        ),
        "source_url": "https://www.ftb.ca.gov/refund/help-refund.html",
        "entity_types": ["ANY"],
        "jurisdictions": ["CA"],
        "simulated": False,
    },
    {
        "topic": "ny_dtf_contact_routing",
        "content": (
            "New York DTF provides official refund-status and notice-response channels, with "
            "separate workflows for lost checks and refund adjustments."
        ),
        "source_url": "https://www.tax.ny.gov/help/contact/contactus-ind.htm",
        "entity_types": ["ANY"],
        "jurisdictions": ["NY"],
        "simulated": False,
    },
    {
        "topic": "ca_business_refunds",
        "content": (
            "California business refunds need separate guidance from personal refunds, since "
            "taxpayer entity type, filing forms, and support channels can differ."
        ),
        "source_url": "https://www.ftb.ca.gov/refund/business-refund.html",
        "entity_types": ["BUSINESS"],
        "jurisdictions": ["CA"],
        "simulated": False,
    },
    {
        "topic": "ca_identity_theft",
        "content": (
            "Suspected California tax-related identity theft should be routed to the official FTB "
            "identity-theft process rather than collecting sensitive identity evidence directly."
        ),
        "source_url": "https://www.ftb.ca.gov/help/scams/identity-theft.html",
        "entity_types": ["ANY"],
        "jurisdictions": ["CA"],
        "simulated": False,
    },
    {
        "topic": "ca_verification_required",
        "content": (
            "A California return selected for security review may require the taxpayer to respond "
            "to a verification notice within a stated period. Confirm whether a notice was actually "
            "received before advising further action, and never request the sensitive contents of "
            "that notice directly."
        ),
        "source_url": "https://www.ftb.ca.gov/help/letters/index.html",
        "entity_types": ["ANY"],
        "jurisdictions": ["CA"],
        "simulated": False,
    },
    {
        "topic": "ca_refund_help",
        "content": (
            "California FTB provides separate guidance for direct deposit issues, prior-year "
            "refunds, changed refunds, and delivery problems -- these are distinct situations that "
            "shouldn't be treated as one generic 'refund is late' case."
        ),
        "source_url": "https://www.ftb.ca.gov/refund/help-refund.html",
        "entity_types": ["ANY"],
        "jurisdictions": ["CA"],
        "simulated": False,
    },
    {
        "topic": "ca_processing_timeframes",
        "content": (
            "California FTB publishes current processing timeframes for e-filed, paper, and amended "
            "returns, and some returns take longer due to accuracy, completeness, fraud, "
            "identity-theft, or disaster-related review. Timeframes change, so don't hard-code a "
            "permanent duration."
        ),
        "source_url": "https://www.ftb.ca.gov/help/time-frames/",
        "entity_types": ["ANY"],
        "jurisdictions": ["CA"],
        "simulated": False,
    },
    {
        "topic": "ny_request_for_information",
        "content": (
            "A New York Request for Information letter (including forms DTF-948 or DTF-948-O) "
            "requires a response by the stated date for processing to continue; route the filer to "
            "the official online response process rather than requesting the notice's sensitive "
            "content directly."
        ),
        "source_url": "https://www.tax.ny.gov/pit/letters/",
        "entity_types": ["ANY"],
        "jurisdictions": ["NY"],
        "simulated": False,
    },
    {
        "topic": "ny_changed_refund",
        "content": (
            "A New York personal income tax refund may be adjusted when information is missing, "
            "inaccurate, or changed during review. This is a distinct situation from a refund that's "
            "simply delayed but otherwise unchanged, and should be explained as such."
        ),
        "source_url": "https://www.tax.ny.gov/pit/file/more_info_refunds.htm",
        "entity_types": ["ANY"],
        "jurisdictions": ["NY"],
        "simulated": False,
    },
    {
        "topic": "ny_check_refund_status",
        "content": (
            "New York provides an official refund-status tool. Direct filers there rather than "
            "asking them to disclose sensitive verification data in a chat interface."
        ),
        "source_url": "https://www.tax.ny.gov/pit/file/refund.htm",
        "entity_types": ["ANY"],
        "jurisdictions": ["NY"],
        "simulated": False,
    },
    {
        "topic": "ny_direct_deposit",
        "content": (
            "E-filing with direct deposit can reduce New York refund delivery time compared to a "
            "paper return and mailed check, but this shouldn't be presented as a guaranteed timeline "
            "for any individual refund."
        ),
        "source_url": "https://www.tax.ny.gov/press/rel/2021/directdeposit030321.htm",
        "entity_types": ["ANY"],
        "jurisdictions": ["NY"],
        "simulated": False,
    },
    {
        "topic": "ny_lost_stolen_check",
        "content": (
            "New York guidance for lost, stolen, destroyed, or uncashed refund checks applies only "
            "when the disbursement method was a paper check and the status shows the refund as "
            "issued."
        ),
        "source_url": "https://www.tax.ny.gov/pit/file/replacement-check.htm",
        "entity_types": ["ANY"],
        "jurisdictions": ["NY"],
        "simulated": False,
    },
    # -- Simulated internal content: demo-authored support-playbook and FAQ framing, not an
    # actual TurboTax/Intuit internal article and not an official IRS/state source (no
    # source_url). Kept distinct via `simulated` so the API/UI never present it as official.
    {
        "topic": "should_i_call_tax_authority",
        "content": (
            "Calling a tax authority usually doesn't accelerate routine processing. It's worth "
            "recommending contact when official guidance says the published timeframe has passed, "
            "the status itself requests action, the refund was issued but not received, or the "
            "filer reports financial hardship."
        ),
        "source_url": None,
        "entity_types": ["ANY"],
        "jurisdictions": ["ANY"],
        "simulated": True,
    },
    {
        "topic": "why_federal_and_state_differ",
        "content": (
            "Federal and state refunds are processed by entirely different authorities with "
            "independent systems, rules, and timelines, so it's normal and expected for a federal "
            "and a state refund on the same year's taxes to show different statuses or arrive at "
            "different times."
        ),
        "source_url": None,
        "entity_types": ["ANY"],
        "jurisdictions": ["ANY"],
        "simulated": True,
    },
    {
        "topic": "no_refund_record_found",
        "content": (
            "If there's no refund record at all, possible explanations include a balance due "
            "instead of a refund, a zero refund, a return that hasn't been accepted yet, an "
            "unsupported authority, or a delay in the status feed itself. Don't assume a refund "
            "exists just because a return was filed."
        ),
        "source_url": None,
        "entity_types": ["ANY"],
        "jurisdictions": ["ANY"],
        "simulated": True,
    },
    {
        "topic": "why_is_my_refund_delayed_overview",
        "content": (
            "A refund can be delayed for many distinct reasons -- routine processing, review, "
            "identity verification, missing information, an adjustment, an offset, amended-return "
            "processing, or a delivery issue -- and the right next step depends on which of these it "
            "actually is, not just the fact that it's taking longer than expected."
        ),
        "source_url": None,
        "entity_types": ["ANY"],
        "jurisdictions": ["ANY"],
        "simulated": True,
    },
    {
        "topic": "business_refund_routing_playbook",
        "content": (
            "For a business refund question: identify the entity type and return form first, "
            "identify whether the authority is federal or state, and don't apply personal-return "
            "timelines automatically. Route payroll, sales-tax, corporate-income-tax, and "
            "partnership matters separately, since each follows its own process."
        ),
        "source_url": None,
        "entity_types": ["BUSINESS"],
        "jurisdictions": ["ANY"],
        "simulated": True,
    },
    {
        "topic": "identity_verification_playbook",
        "content": (
            "When helping with an identity-verification hold: never ask for a Social Security "
            "number, account numbers, identity-document images, or a notice access code. Confirm "
            "the issuing authority and notice type, link only to the authority's own official "
            "verification channel, explain that processing can stay paused until verification "
            "completes, and escalate anything that sounds like suspected identity theft rather than "
            "a routine hold."
        ),
        "source_url": None,
        "entity_types": ["ANY"],
        "jurisdictions": ["ANY"],
        "simulated": True,
    },
    {
        "topic": "reduced_refund_playbook",
        "content": (
            "When a refund comes back smaller than expected: first determine whether it's an "
            "adjustment (the authority recalculated the return) or an offset (funds applied to a "
            "past-due debt), since those have different explanations and different agencies to "
            "contact. Retrieve the authority-specific notice guidance, explain the distinction "
            "clearly, and direct any dispute to the agency named in the notice rather than guessing."
        ),
        "source_url": None,
        "entity_types": ["ANY"],
        "jurisdictions": ["ANY"],
        "simulated": True,
    },
    {
        "topic": "refund_sent_not_received_playbook",
        "content": (
            "When a refund shows as sent but hasn't arrived: confirm the issue date and disbursement "
            "method first. For direct deposit, check whether the normal waiting period has actually "
            "elapsed before assuming a problem. For a paper check, use the authority-specific "
            "lost-check process. Only recommend a refund trace once the official criteria for one "
            "are met, and never advise filing a duplicate return."
        ),
        "source_url": None,
        "entity_types": ["ANY"],
        "jurisdictions": ["ANY"],
        "simulated": True,
    },
    {
        "topic": "refund_under_review_playbook",
        "content": (
            "When a refund is under review: confirm whether the authority is federal or state and "
            "whether a notice was actually received. If no action has been requested, it's enough to "
            "explain that review is ongoing and show the last known update. If a notice does request "
            "information, direct the filer to the authority's secure official response channel. "
            "Escalate if the published timeframe has been exceeded or the filer reports financial "
            "hardship."
        ),
        "source_url": None,
        "entity_types": ["ANY"],
        "jurisdictions": ["ANY"],
        "simulated": True,
    },
]

# The finite set of situations the app needs guidance for. Originally only FLAGGED/
# UNDER_REVIEW surfaced a guidance card (see ActionGuidanceCard.tsx) -- APPROVED and SENT
# were added once real content existed for "what happens after approval" and "sent but not
# received" situations. RECEIVED and DEPOSITED remain excluded: RECEIVED carries no signal
# yet beyond "filed", and DEPOSITED means the refund has already landed -- there's nothing
# to guide someone through at either end of the lifecycle.
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
    {
        "situation_key": "APPROVED_INDIVIDUAL_FEDERAL",
        "entity_type": "INDIVIDUAL",
        "jurisdiction": "FEDERAL",
        "description": "An individual's federal tax refund has been approved by the IRS and is expected to be sent soon, though it may still be reduced by a debt offset.",
    },
    {
        "situation_key": "APPROVED_INDIVIDUAL_STATE",
        "entity_type": "INDIVIDUAL",
        "jurisdiction": "STATE",
        "description": "An individual's state tax refund has been approved by the state tax agency and is expected to be sent soon.",
    },
    {
        "situation_key": "APPROVED_BUSINESS_FEDERAL",
        "entity_type": "BUSINESS",
        "jurisdiction": "FEDERAL",
        "description": "A business federal tax refund has been approved by the IRS and is expected to be sent soon.",
    },
    {
        "situation_key": "APPROVED_BUSINESS_STATE",
        "entity_type": "BUSINESS",
        "jurisdiction": "STATE",
        "description": "A business state tax refund has been approved by the state tax agency and is expected to be sent soon.",
    },
    {
        "situation_key": "SENT_INDIVIDUAL_FEDERAL",
        "entity_type": "INDIVIDUAL",
        "jurisdiction": "FEDERAL",
        "description": "An individual's federal tax refund has been sent by the IRS but the filer hasn't received it yet, whether by direct deposit or paper check.",
    },
    {
        "situation_key": "SENT_INDIVIDUAL_STATE",
        "entity_type": "INDIVIDUAL",
        "jurisdiction": "STATE",
        "description": "An individual's state tax refund has been sent by the state tax agency but the filer hasn't received it yet.",
    },
    {
        "situation_key": "SENT_BUSINESS_FEDERAL",
        "entity_type": "BUSINESS",
        "jurisdiction": "FEDERAL",
        "description": "A business federal tax refund has been sent by the IRS but the business hasn't received it yet.",
    },
    {
        "situation_key": "SENT_BUSINESS_STATE",
        "entity_type": "BUSINESS",
        "jurisdiction": "STATE",
        "description": "A business state tax refund has been sent by the state tax agency but the business hasn't received it yet.",
    },
]
