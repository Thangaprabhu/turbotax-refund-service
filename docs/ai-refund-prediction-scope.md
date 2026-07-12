# AI Scope: Refund Status Prediction & Guided Client Experience

**Author:** Thangaprabhu Chandrasekhar
**Status:** Draft for review
**Audience:** Staff+ engineering review, product, ML platform

## 1. Problem Statement

Today, `FilingResponse` already carries `aiPredictedDays`, `aiConfidence`, and `aiModelVersion` fields (see [FilingItem.java](../src/main/java/com/turbotax/refund/dynamodb/FilingItem.java)), and the client renders them when present ([TaxpayerDetailPage.tsx:208-220](../frontend/src/pages/TaxpayerDetailPage.tsx#L208-L220)). Nothing populates them yet — there is no prediction engine, and the client gives users no guidance beyond a status badge and a raw day count.

Two gaps to close:

1. **Prediction**: when a filing's `irsStatus` is not yet `DEPOSITED`, estimate when the refund will land, with a calibrated confidence signal.
2. **Guidance**: give users a clear, low-anxiety path to understand *where their refund is*, *what happens next*, and *what to do* if something looks wrong (`FLAGGED`, `UNDER_REVIEW`, or an estimate that has slipped).

This is a regulated, high-anxiety, money-related domain (IRS refunds). The design bar is not "most accurate model" — it's *trustworthy, explainable, and safe to be wrong in public*.

## 2. Goals / Non-Goals

**Goals**
- Populate a refund-timing estimate + confidence for any filing not yet `DEPOSITED`, refreshed as status changes arrive.
- Present that estimate and the overall journey in a way a non-technical filer understands in under 5 seconds.
- Turn `FLAGGED` / `UNDER_REVIEW` / slipped-estimate states into a specific, actionable next step instead of a dead end.
- Make the prediction pipeline swappable — start cheap, upgrade without a client contract change.

**Non-goals**
- Replacing the IRS as source of truth for status (we're an estimator layered on top of `irsStatus`, not an authority).
- Guaranteeing a delivery date (legal/trust risk) — we communicate *ranges and confidence*, never a promise.
- Real-time IRS transcript scraping/integration in v1 (assume `irsStatus` updates arrive via the existing Kafka pipeline from whatever adapter — `IRS_IMF`/`IRS_BMF` — already populates `RefundStatusUpdatedEvent`).

## 3. Requirements

### Functional
- **(a)** User requests refund status → if not yet `DEPOSITED`, show an AI-estimated time-to-refund.
- **(b)** Client clearly guides the user on *how* to check status and *what the states mean*.
- **(c)** Client tells the user what actions are available when there's a problem (delay, flag, review).

### Non-functional
- **Latency**: estimate must be available synchronously on `GET /filings` reads (p99 < 200ms) — compute is async/offline, reads are lookups, not live inference.
- **Explainability**: every estimate must have a human-readable "why" — regulatory and trust requirement for financial estimates.
- **Privacy**: filings contain PII (`refundAmountEncrypted` is already KMS-encrypted via `PiiEncryptionService`). Model features and logs must not leak raw PII; training data must be de-identified.
- **Resilience**: if the prediction pipeline is down or cold-started (no model yet, or unseen filing shape), the system must degrade to a safe deterministic fallback, never `null`-and-silent.
- **Auditability**: `aiModelVersion` must be persisted per-prediction (field already exists) so we can trace any estimate back to the exact model/ruleset that produced it — important if a user disputes an estimate.

## 4. Solution Options — Prediction Engine

Five options, ordered by sophistication. They are not mutually exclusive — the recommendation (§5) chains them.

### Option A — Deterministic rules engine (IRS cycle chart lookup)
Hard-code the IRS's own published guidance: ~90% of e-filed refunds are issued within 21 days of `RECEIVED`; add known seasonal holds (e.g., EITC/ACTC-claiming returns are held by law until mid-February regardless of filing date); map `APPROVED → SENT` at a fixed offset (~1-2 days) and `SENT → DEPOSITED` by disbursement method (ACH ~1-5 days, paper check ~2-4 weeks).

| | |
|---|---|
| Data needed | None beyond `formType`, `filingDate`, `irsStatus`, jurisdiction — all already on `FilingItem` |
| Accuracy | Directionally correct, not personalized; matches IRS's own public guidance |
| Explainability | Trivial — literally cites IRS-published rules |
| Effort | Days |
| Cost | ~$0 compute |
| Risk | Low — worst case it's exactly as informative as IRS.gov |

### Option B — Classical ML regression (gradient-boosted trees)
Train XGBoost/LightGBM to predict days-to-deposit from historical `FilingItem` + `statusHistory` records, using features like form type, jurisdiction, filer type, day-of-week/season of filing, whether flagged, prior status-transition durations, adapter used (`IRS_IMF` vs `IRS_BMF`).

| | |
|---|---|
| Data needed | Thousands of completed (`DEPOSITED`) historical filings with full `statusHistory` |
| Accuracy | Materially better than A once enough volume exists; captures interaction effects (e.g., "business + `F1120` + `UNDER_REVIEW`" typically runs long) |
| Explainability | SHAP values per prediction — good, requires extra plumbing |
| Effort | 3-6 weeks (feature pipeline, training, serving) |
| Cost | Low (tree models are cheap to serve, even in-JVM via ONNX or a small Python sidecar) |
| Risk | **Cold start** — no signal until we've accumulated a season or more of completed filings. Needs a fallback. |

### Option C — Survival / time-to-event model (statistically correct framing)
Reframe the actual problem: "days until deposit" is a **time-to-event** problem with **right-censored** data — every currently-pending filing is a censored observation (we don't yet know its outcome), and naively training a regressor only on completed filings introduces survivorship bias (it never learns from the filings still stuck in `UNDER_REVIEW`, which are disproportionately the slow ones). Use a Cox proportional-hazards or Accelerated Failure Time model, or a discrete-time hazard model, to estimate a full distribution (e.g., "70% chance of deposit within 10 days, 95% within 21 days") rather than a single point estimate.

| | |
|---|---|
| Data needed | Same as B, but *including* still-pending filings as censored records |
| Accuracy | Best-calibrated of the statistical options; naturally produces a confidence *interval*, not just a point + scalar confidence |
| Explainability | Hazard ratios per feature are interpretable ("under review adds ~2.3x expected wait") |
| Effort | 4-8 weeks — less common tooling, more statistical rigor needed on the team |
| Cost | Low, similar to B |
| Risk | Team unfamiliarity with survival analysis; still has a cold-start problem shared with B |

### Option D — LLM-assisted explanation layer (not the numeric predictor)
Use an LLM to turn the structured signal (status, model estimate, confidence, known IRS seasonal factors, status history) into a natural-language explanation and next-step guidance — e.g. *"Your return is under additional review, which the IRS has flagged for identity verification (F1040s claiming EITC are checked more heavily this year). Typical review takes 45-60 days from March filings."* The LLM never invents the number; it narrates a number Option A/B/C already computed, and is not given raw PII.

| | |
|---|---|
| Data needed | None additional — consumes existing model output + status metadata |
| Accuracy | N/A (not the numeric estimator) — but a real risk of *hallucinated* reasons for delay if not tightly grounded/constrained |
| Explainability | This *is* the explainability layer for whichever engine produces the number |
| Effort | 1-2 weeks once A or B/C exists |
| Cost | Small per-request LLM call cost (or batch-generate template variants and cache) |
| Risk | Must be strictly grounded (no free-form generation of dates/amounts) — treat as a templating/explanation problem, not open-ended chat, to avoid financial-advice liability |

### Option E — Buy/managed (vendor tax-data aggregator + hosted ML)
Integrate a third-party tax-data platform (e.g., an IRS transcript aggregator) that already exposes refund-timing predictions, and/or use a managed ML platform (SageMaker Autopilot / Vertex AI) instead of self-hosting B/C.

| | |
|---|---|
| Data needed | Vendor contract; still need our own historical data to validate vendor claims |
| Accuracy | Unknown/vendor-dependent; likely trained on broader population data than we'll have in year 1 |
| Explainability | Weakest — vendor is a black box unless they expose SHAP/feature importance |
| Effort | Procurement + integration timeline, not engineering-bound |
| Cost | Ongoing vendor fee, scales with volume |
| Risk | Data-sharing of taxpayer PII with a third party — significant compliance review (IRS Pub 1075 / Safeguards implications), vendor lock-in |

### Decision matrix

| Option | Time to ship | Accuracy ceiling | Explainability | Ongoing cost | Cold-start safe | Compliance risk |
|---|---|---|---|---|---|---|
| A. Rules engine | Days | Low-Medium | Excellent | ~$0 | **Yes** | None |
| B. GBM regression | Weeks | Medium-High | Good (SHAP) | Low | No | Low |
| C. Survival model | Weeks-Months | High, best calibrated | Good (hazard ratios) | Low | No | Low |
| D. LLM explanation | Days-Weeks (layered) | N/A | N/A (is the layer) | Low-Medium | Yes (once base exists) | Medium (grounding) |
| E. Vendor/managed | Procurement-bound | Unknown | Poor | High, recurring | Yes | High (data sharing) |

## 5. Recommendation

Ship in layers rather than picking one option — this is the Staff-level insight that resolves the "which one" question:

1. **Phase 0 (baseline, week 1-2):** Ship **Option A** as `aiModelVersion = "rules-v1"`. This is the permanent fallback, not a throwaway — every later model routes through it when confidence is low or data is out of distribution.
2. **Phase 1 (primary model, month 2-3):** Introduce **Option C (survival model)** over **Option B (GBM)** if the team can support it — it directly produces the confidence interval the UI needs and correctly uses pending filings as training signal instead of discarding them. If statistical tooling maturity is a blocker, ship B first as a stepping stone; the feature pipeline is shared between B and C, so this isn't wasted work.
3. **Phase 2 (explanation, month 3-4):** Layer **Option D** on top of whichever numeric model is live, strictly grounded (template-constrained generation, fed only the model's own output + public IRS rules — never raw taxpayer PII).
4. **Option E is a fallback plan, not a starting point** — revisit only if in-house data volume proves too small to train B/C reliably (e.g., a new market segment with little history), and route any vendor evaluation through privacy/legal review first given PII-sharing implications.

**Model selection logic at inference time**: try the primary model (B/C); if its own confidence is below a threshold or the filing's feature combination is out of the training distribution, fall back to A. This means `aiConfidence` also implicitly encodes *which* engine answered, and the client's confidence-tier language (§7) stays consistent regardless of which engine is live.

## 6. System Architecture

```
FilingCreatedEvent ──┐
RefundStatusUpdated ─┼─▶ Kafka ──▶ [refund-prediction-service] ──▶ writes aiPredictedDays,
Event                │              (new consumer)                  aiConfidence, aiModelVersion
                      │                    │                         back onto FilingItem
                      │                    ▼                         (DynamoDB)
                      │          Feature lookup (status history,
                      │           form type, jurisdiction, season)
                      │                    │
                      │          ┌─────────┴─────────┐
                      │          │ Model registry:    │
                      │          │ rules-v1 (always)  │
                      │          │ survival-v1 (opt)  │
                      │          └────────────────────┘
                      ▼
              FilingService (existing) — unchanged read path,
              GET /filings returns whatever is on the item
```

- **New component**: `refund-prediction-service` consumes the existing `filingCreated` / `refundStatusUpdated` Kafka topics (already produced by [FilingEventProducer](../src/main/java/com/turbotax/refund/kafka/producer/FilingEventProducer.java)) — no new producer-side work, no change to `FilingController`'s synchronous read path, no added read latency.
- Prediction runs **async on write**, not on read — `GET /filings` (`FilingService.findAll`) stays a plain DynamoDB read; whatever `aiPredictedDays`/`aiConfidence`/`aiModelVersion` are on the item at read time is what's returned. This is why explainability (D) can be relatively slow/LLM-backed without hurting API latency: it's precomputed and cached alongside the numeric estimate, not generated per-request.
- Model artifacts versioned in a registry (even a simple S3 + manifest is enough at this scale); `aiModelVersion` on `FilingItem` is the audit trail already designed into the schema.
- Observability: this repo already runs OTel + Prometheus + Grafana ([otel-collector.yml](../otel-collector.yml), [prometheus.yml](../prometheus.yml)) — add `TaxMetrics`-style counters for prediction volume by engine (`rules-v1` vs `survival-v1`), and a gauge for realized error (`|predicted_days - actual_days|`) once a filing reaches `DEPOSITED`, so drift is visible on the existing dashboards without new tooling.
- Retraining: batch job reads completed (`DEPOSITED`) + still-pending (censored) filings from DynamoDB, retrains offline, publishes a new model version; shadow-serve before promoting.

## 7. Client Experience Design

Design principle: **the user should never have to interpret a raw number or a status enum themselves.** Every screen answers "where is my money," "what happens next," and "is this normal" without the user needing to know what `UNDER_REVIEW` means internally.

### (b) Checking status — the primary flow
`TaxpayerDetailPage` already lists filings with a status badge; extend the expanded filing card:

- **Refund timeline stepper** (new `RefundTimeline.tsx`): visually maps `RECEIVED → APPROVED → SENT → DEPOSITED` as a horizontal progress stepper, with `FLAGGED`/`UNDER_REVIEW` rendered as a branch off the main line, not a dead state — reduces the "did something break" anxiety a bare badge causes.
- **Estimate framed as a range + confidence tier, not raw stats**: instead of "`aiConfidence: 0.62`", show *"Likely between Jul 14–Jul 21 · Moderate confidence"*. Map confidence buckets (e.g. High ≥0.8, Moderate 0.5–0.8, Low <0.5) to plain language — never surface the raw float to end users; keep it in the API/devtools for support staff.
- **"Why this estimate" disclosure** (collapsed by default): the Option D explanation text, e.g. *"Based on IRS Where's My Refund guidance for e-filed 1040s this season, plus your return's current status."* Always includes a one-line disclaimer: *"This is an estimate, not confirmed by the IRS."*
- Status history (already implemented) stays as the detailed audit trail for power users/CPAs who want it.

### (c) What to do about issues — guided remediation
New `ActionGuidanceCard.tsx`, rendered only when relevant, mapping `irsStatus` (and estimate-slippage) to a specific action set instead of a wall of text:

| State | Guidance shown |
|---|---|
| `FLAGGED` | "Your return needs attention." + link to IRS identity-verification steps, "Contact support" CTA, expected resolution range |
| `UNDER_REVIEW` | "This is taking longer than average." + explanation of common causes (EITC/ACTC hold, identity check, math-error notice) + "what happens automatically vs. what you need to do" |
| Estimate slipped past prior window | Proactive banner: "Your estimate has moved. Here's why," rather than making the user notice the date changed themselves |
| `DEPOSITED` | Confirmation state, no action needed — closes the loop |

- **Proactive notification** (email/push, new preference in taxpayer settings) when `irsStatus` changes or the estimate shifts materially — this directly targets the usability goal in (b)/(c): users shouldn't have to keep re-opening the app to "check." This is the single highest-leverage usability change relative to engineering cost, since the Kafka event that would trigger it already exists.
- Empty/loading/error states for the prediction itself: if `aiPredictedDays` is `null` (cold start, no model coverage yet), show the rules-engine-derived "typical range for this form type" rather than blank space — client should never render "AI has no idea," only degrade gracefully to whichever fallback engine answered.

## 8. Privacy & Compliance

- Tax refund data falls under IRS Pub 1075 safeguarding expectations for FTI-adjacent data; existing `PiiEncryptionService`/KMS handling of `refundAmountEncrypted` sets the bar — the prediction pipeline must not log or feature-engineer on raw refund amounts or taxpayer identifiers. Feature vectors should use hashed/tokenized taxpayer references, not SSN/EIN.
- LLM explanation layer (Option D) must be called with de-identified, structured inputs only (status enum, dates, form type) — never raw taxpayer text or PII — and outputs constrained to a template/slot-filling pattern to prevent hallucinated specifics (dollar amounts, guaranteed dates).
- Any vendor path (Option E) requires a data-sharing/compliance review before evaluation, not after.

## 9. Success Metrics

- **Model quality**: MAE (days) between `aiPredictedDays` and actual date reaching `DEPOSITED`; calibration (does "80% confidence" actually resolve within the stated window ~80% of the time?).
- **Product**: reduction in repeat-check frequency per filing (proxy for reduced anxiety/uncertainty), CSAT on the refund-tracking flow, reduction in support contact volume for "where's my refund"-type questions.
- **Trust**: rate of users reporting the estimate as "wrong" or disputing it — should trend down phase over phase, not just accuracy MAE.

## 10. Open Questions

- What historical filing volume currently exists to train B/C, and is it enough to segment by form type/jurisdiction without overfitting sparse cells (e.g., `F941` + small state jurisdictions)?
- Does legal want a permanent, always-visible "estimate, not a guarantee" disclaimer, or is contextual (tooltip) sufficient?
- Notification channel ownership — does this repo's scope include email/push infra, or does it hand off to a separate notification service?
