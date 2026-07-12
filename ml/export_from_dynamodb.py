"""
Scans the ml-training-filings DynamoDB table and writes a SageMaker-ready CSV.

Stands in for the real "DynamoDB export to S3" step (or a scheduled Glue job)
that would run against production data periodically -- SageMaker training
jobs cannot read from DynamoDB directly, only S3/FSx/EFS, so this materializes
the table into the flat file format Autopilot/XGBoost expects.

Run:
    ml/.venv/bin/python ml/export_from_dynamodb.py
"""
import csv
import os

import boto3

TABLE_NAME = "ml-training-filings"
OUTPUT_PATH = os.path.join(os.path.dirname(__file__), "data", "synthetic_filings_from_dynamodb.csv")
ENDPOINT_URL = os.environ.get("DYNAMODB_ENDPOINT", "http://localhost:8000")
REGION = os.environ.get("AWS_REGION", "us-east-1")

COLUMNS = [
    "filing_id", "taxpayer_type", "form_type", "jurisdiction", "filing_date",
    "filing_month", "filing_day_of_week", "is_peak_season", "claims_eitc_actc",
    "event_observed", "observed_days", "final_status",
]


def scan_all(table):
    items = []
    resp = table.scan()
    items.extend(resp["Items"])
    while "LastEvaluatedKey" in resp:
        resp = table.scan(ExclusiveStartKey=resp["LastEvaluatedKey"])
        items.extend(resp["Items"])
    return items


def main():
    session = boto3.session.Session(
        aws_access_key_id=os.environ.get("AWS_ACCESS_KEY_ID", "local"),
        aws_secret_access_key=os.environ.get("AWS_SECRET_ACCESS_KEY", "local"),
        region_name=REGION,
    )
    table = session.resource("dynamodb", endpoint_url=ENDPOINT_URL).Table(TABLE_NAME)

    items = scan_all(table)
    items.sort(key=lambda r: r["filing_id"])

    with open(OUTPUT_PATH, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=COLUMNS)
        writer.writeheader()
        for item in items:
            writer.writerow({c: item.get(c) for c in COLUMNS})

    print(f"exported {len(items)} rows from {TABLE_NAME} to {OUTPUT_PATH}")


if __name__ == "__main__":
    main()
