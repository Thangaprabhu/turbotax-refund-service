"""
Seeds a dedicated demo account with 100 taxpayers and 100 filings (varied
form type / jurisdiction / tax year / status) so pagination has something
real to page through. Deliberately uses a fresh throwaway account rather than
the real Cpa1FromCompanyA@gmail.com account, to avoid cluttering it.

Run (backend must be up on localhost:8080):
    ml/.venv/bin/python ml/seed_pagination_demo.py
"""
import random

import requests

BASE = "http://localhost:8080/api/v1"
EMAIL = "pagination-demo@example.com"
PASSWORD = "password123"

FIRST_NAMES = ["Alex", "Jordan", "Sam", "Taylor", "Morgan", "Casey", "Riley", "Jamie", "Drew", "Avery"]
COMPANY_WORDS = ["Summit", "Harbor", "Cascade", "Meridian", "Redwood", "Anchor", "Beacon", "Union", "Vantage", "Bright"]
STATES = ["CA", "NY", "TX", "FL", "IL", "PA", "OH", "GA", "NC", "WA"]
FORM_TYPES = ["F1040", "F1120", "F1065", "F941"]
NON_RECEIVED_STATUSES = ["APPROVED", "SENT", "DEPOSITED", "FLAGGED", "UNDER_REVIEW"]

random.seed(7)


def auth_headers(token):
    return {"Authorization": f"Bearer {token}"}


def register_or_login():
    r = requests.post(f"{BASE}/auth/register", json={"email": EMAIL, "password": PASSWORD, "accountType": "CPA"})
    if r.status_code == 409:
        r = requests.post(f"{BASE}/auth/login", json={"email": EMAIL, "password": PASSWORD})
    r.raise_for_status()
    return r.json()["accessToken"]


def create_taxpayer(token, i):
    if random.random() < 0.6:
        body = {
            "taxpayerType": "INDIVIDUAL",
            "taxId": f"{200 + i:03d}-{10 + (i % 89):02d}-{1000 + i:04d}",
            "displayName": f"{random.choice(FIRST_NAMES)} Client {i}",
            "entityType": None,
            "stateOfReg": random.choice(STATES),
        }
    else:
        body = {
            "taxpayerType": "BUSINESS",
            "taxId": f"{10 + (i % 89):02d}-{2000000 + i:07d}",
            "displayName": f"{random.choice(COMPANY_WORDS)} {i} LLC",
            "entityType": random.choice(["LLC", "S-Corp", "Partnership", "C-Corp"]),
            "stateOfReg": random.choice(STATES),
        }
    r = requests.post(f"{BASE}/taxpayers", json=body, headers=auth_headers(token))
    r.raise_for_status()
    return r.json()["id"]


def create_filing(token, taxpayer_id, tax_year, form_type, jurisdiction):
    body = {
        "formType": form_type,
        "taxYear": tax_year,
        "jurisdiction": jurisdiction,
        "filingDate": f"{tax_year}-{random.randint(1, 12):02d}-{random.randint(1, 28):02d}",
    }
    r = requests.post(f"{BASE}/taxpayers/{taxpayer_id}/filings", json=body, headers=auth_headers(token))
    if r.status_code == 409:
        return None
    r.raise_for_status()
    return r.json()["sk"]


def update_status(token, taxpayer_id, sk, status):
    r = requests.patch(
        f"{BASE}/taxpayers/{taxpayer_id}/filings/{sk.replace('#', '%23')}/status",
        json={"irsStatus": status},
        headers=auth_headers(token),
    )
    r.raise_for_status()


def existing_taxpayer_count(token):
    r = requests.get(f"{BASE}/taxpayers", params={"page": 0, "size": 1}, headers=auth_headers(token))
    r.raise_for_status()
    return r.json()["totalElements"], r.json()["content"]


def main():
    token = register_or_login()
    print(f"authenticated as {EMAIL}")

    existing_count, existing_content = existing_taxpayer_count(token)
    if existing_count >= 100:
        filing_taxpayer_id = existing_content[0]["id"]
        print(f"{existing_count} taxpayers already exist, skipping creation")
    else:
        taxpayer_ids = [create_taxpayer(token, i) for i in range(100)]
        print(f"created {len(taxpayer_ids)} taxpayers")
        filing_taxpayer_id = taxpayer_ids[0]
    combos = [
        (year, form, juris)
        for year in ("2022", "2023", "2024")
        for form in FORM_TYPES
        for juris in ["FEDERAL"] + STATES
    ]
    random.shuffle(combos)
    combos = combos[:100]

    created, status_changed = 0, 0
    for tax_year, form_type, jurisdiction in combos:
        sk = create_filing(token, filing_taxpayer_id, tax_year, form_type, jurisdiction)
        if sk is None:
            continue
        created += 1
        if random.random() < 0.6:
            status = random.choices(NON_RECEIVED_STATUSES, weights=[25, 20, 20, 15, 20])[0]
            update_status(token, filing_taxpayer_id, sk, status)
            status_changed += 1

    print(f"created {created} filings under taxpayer {filing_taxpayer_id} ({status_changed} status-varied)")
    print()
    print(f"login: {EMAIL} / {PASSWORD}")
    print(f"filings-heavy taxpayer id: {filing_taxpayer_id}")


if __name__ == "__main__":
    main()
