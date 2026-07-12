"""
Generates a synthetic refund-timing training set for the SageMaker model
described in docs/ai-refund-prediction-scope.md (Option B/C: replace/augment
rules-v1 with a trained model).

Only stdlib is used (random, csv, datetime) -- no numpy/pandas required.

Modeled effects, so a trained model has real signal to find beyond rules-v1:
  - form-type/jurisdiction baseline cycle times (same shape as rules-v1, but
    with realistic gamma-distributed variance instead of a flat number)
  - the PATH Act: EITC/ACTC-claiming returns are held by law until ~Feb 27,
    regardless of filing date or processing speed -- rules-v1 does not know
    this, so it is a genuine opportunity for a trained model to beat it
  - state jurisdictions running slower/more variable than federal e-file
  - review/flag events adding extra, highly-skewed delay
  - right-censoring: ~10-20% of rows are still pending as of the simulated
    snapshot date, with observed_days as a lower bound rather than the true
    total -- this is what lets a survival model (Option C) use them instead
    of discarding them, unlike a naive regression on completed filings only.

Columns intentionally EXCLUDED to avoid leakage: whether a filing was
reviewed/flagged is used internally to inject delay, but is never written to
the output -- at prediction time (right after RECEIVED), you don't know yet
whether a filing will be reviewed, so a model can't be trained on it.
"""
import csv
import os
import random
from datetime import date, timedelta

random.seed(42)

N = 20_000
TAX_YEAR = 2025
OUTPUT_PATH = os.path.join(os.path.dirname(__file__), "data", "synthetic_filings.csv")
SNAPSHOT_DATE = date(TAX_YEAR, 9, 1)  # simulated "today" the training pull was taken

FEDERAL_BASE_MEAN_DAYS = {"F1040": 16, "F1120": 60, "F1065": 55, "F941": 130}
FEDERAL_BASE_SHAPE = {"F1040": 6.0, "F1120": 3.0, "F1065": 3.0, "F941": 1.8}  # lower = more skew/variance
STATE_MULTIPLIER = 1.35
STATES = ["CA", "NY", "TX", "FL", "IL", "PA", "OH", "GA", "NC"]
STATE_WEIGHTS = [12, 8, 7, 6, 5, 4, 3, 3, 2]

EITC_ACTC_HOLD_CUTOFF = (2, 15)   # only holds returns filed before this date
EITC_ACTC_HOLD_RELEASE = (2, 27)  # PATH Act: refunds not released before this date


def random_filing_date(form_type):
    start, end = date(TAX_YEAR, 1, 20), date(TAX_YEAR, 10, 15)
    total_days = (end - start).days
    peak = 75 if form_type == "F1040" else 55  # individuals cluster near Apr 15, business near Mar 15
    offset = int(random.triangular(0, total_days, peak))
    return start + timedelta(days=offset)


def sample_duration(mean, shape):
    return random.gammavariate(shape, mean / shape)


def build_filing(i):
    taxpayer_type = "INDIVIDUAL" if random.random() < 0.78 else "BUSINESS"
    form_type = "F1040" if taxpayer_type == "INDIVIDUAL" else random.choices(
        ["F1120", "F1065", "F941"], weights=[50, 30, 20]
    )[0]

    is_federal = random.random() < 0.55
    jurisdiction = "FEDERAL" if is_federal else random.choices(STATES, weights=STATE_WEIGHTS)[0]

    filing_date = random_filing_date(form_type)
    claims_eitc_actc = 1 if form_type == "F1040" and random.random() < 0.28 else 0

    review_prob = 0.06 + (0.05 if claims_eitc_actc else 0) + (0.08 if form_type in ("F1120", "F941") else 0)
    was_reviewed = random.random() < review_prob
    was_flagged = was_reviewed and random.random() < 0.25

    duration = sample_duration(FEDERAL_BASE_MEAN_DAYS[form_type], FEDERAL_BASE_SHAPE[form_type])
    if was_reviewed:
        duration += random.gammavariate(3.0, 10.0)
    if was_flagged:
        duration += random.gammavariate(2.0, 15.0)
    if not is_federal:
        duration *= STATE_MULTIPLIER

    deposit_date = filing_date + timedelta(days=duration)

    if claims_eitc_actc and (filing_date.month, filing_date.day) < EITC_ACTC_HOLD_CUTOFF:
        hold_release = date(TAX_YEAR, *EITC_ACTC_HOLD_RELEASE) + timedelta(days=random.randint(0, 10))
        if hold_release > deposit_date:
            deposit_date = hold_release

    true_total_days = max((deposit_date - filing_date).days, 1)

    return {
        "taxpayer_type": taxpayer_type,
        "form_type": form_type,
        "jurisdiction": jurisdiction,
        "filing_date": filing_date,
        "claims_eitc_actc": claims_eitc_actc,
        "true_total_days": true_total_days,
    }


def censor_and_finalize(i, f):
    filing_date = f["filing_date"]
    true_total = f["true_total_days"]
    true_deposit_date = filing_date + timedelta(days=true_total)

    if true_deposit_date <= SNAPSHOT_DATE:
        event_observed, observed_days, final_status = 1, true_total, "DEPOSITED"
    else:
        event_observed = 0
        observed_days = max((SNAPSHOT_DATE - filing_date).days, 1)
        fraction = observed_days / true_total
        if fraction < 0.3:
            final_status = "RECEIVED"
        elif fraction < 0.55:
            final_status = random.choices(["APPROVED", "UNDER_REVIEW"], weights=[70, 30])[0]
        elif fraction < 0.85:
            final_status = random.choices(["APPROVED", "UNDER_REVIEW", "FLAGGED"], weights=[50, 35, 15])[0]
        else:
            final_status = random.choices(["APPROVED", "SENT"], weights=[40, 60])[0]

    return {
        "filing_id": f"SYN-{i:06d}",
        "taxpayer_type": f["taxpayer_type"],
        "form_type": f["form_type"],
        "jurisdiction": f["jurisdiction"],
        "filing_date": filing_date.isoformat(),
        "filing_month": filing_date.month,
        "filing_day_of_week": filing_date.weekday(),
        "is_peak_season": 1 if filing_date.month in (3, 4) else 0,
        "claims_eitc_actc": f["claims_eitc_actc"],
        "event_observed": event_observed,
        "observed_days": observed_days,
        "final_status": final_status,
    }


def main():
    rows = [censor_and_finalize(i, build_filing(i)) for i in range(N)]

    os.makedirs(os.path.dirname(OUTPUT_PATH), exist_ok=True)
    with open(OUTPUT_PATH, "w", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)

    censored = sum(1 for r in rows if r["event_observed"] == 0)
    print(f"wrote {len(rows)} rows to {OUTPUT_PATH}")
    print(f"censored (still pending): {censored} ({100 * censored / len(rows):.1f}%)")

    by_form = {}
    for r in rows:
        by_form.setdefault(r["form_type"], []).append(r["observed_days"])
    for form_type, durations in sorted(by_form.items()):
        durations.sort()
        n = len(durations)
        print(f"  {form_type}: n={n}, median={durations[n // 2]}, p90={durations[int(n * 0.9)]}")


if __name__ == "__main__":
    main()
