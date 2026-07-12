"""
Loads the synthetic refund-filing training rows (ml/data/synthetic_filings.csv)
into a DynamoDB table, standing in for the production pattern where completed
and censored filings accumulate in DynamoDB as the system of record, and get
exported to S3 for SageMaker training (see export_from_dynamodb.py).

Defaults to the project's dynamodb-local (docker-compose.yml). Point at real
AWS by unsetting DYNAMODB_ENDPOINT and supplying real credentials/region.

Run:
    ml/.venv/bin/python ml/load_to_dynamodb.py
"""
import csv
import os

import boto3

TABLE_NAME = "ml-training-filings"
CSV_PATH = os.path.join(os.path.dirname(__file__), "data", "synthetic_filings.csv")
ENDPOINT_URL = os.environ.get("DYNAMODB_ENDPOINT", "http://localhost:8000")
REGION = os.environ.get("AWS_REGION", "us-east-1")

NUMERIC_FIELDS = {
    "filing_month", "filing_day_of_week", "is_peak_season",
    "claims_eitc_actc", "event_observed", "observed_days",
}


def get_client_and_resource():
    session = boto3.session.Session(
        aws_access_key_id=os.environ.get("AWS_ACCESS_KEY_ID", "local"),
        aws_secret_access_key=os.environ.get("AWS_SECRET_ACCESS_KEY", "local"),
        region_name=REGION,
    )
    client = session.client("dynamodb", endpoint_url=ENDPOINT_URL)
    resource = session.resource("dynamodb", endpoint_url=ENDPOINT_URL)
    return client, resource


def ensure_table(client):
    if TABLE_NAME in client.list_tables()["TableNames"]:
        print(f"table {TABLE_NAME} already exists")
        return
    client.create_table(
        TableName=TABLE_NAME,
        KeySchema=[{"AttributeName": "filing_id", "KeyType": "HASH"}],
        AttributeDefinitions=[{"AttributeName": "filing_id", "AttributeType": "S"}],
        BillingMode="PAY_PER_REQUEST",
    )
    client.get_waiter("table_exists").wait(TableName=TABLE_NAME)
    print(f"created table {TABLE_NAME}")


def load_rows(resource):
    table = resource.Table(TABLE_NAME)
    count = 0
    with open(CSV_PATH, newline="") as f:
        reader = csv.DictReader(f)
        with table.batch_writer(overwrite_by_pkeys=["filing_id"]) as batch:
            for row in reader:
                item = {k: (int(v) if k in NUMERIC_FIELDS else v) for k, v in row.items()}
                batch.put_item(Item=item)
                count += 1
    print(f"loaded {count} rows into {TABLE_NAME}")


def main():
    client, resource = get_client_and_resource()
    ensure_table(client)
    load_rows(resource)


if __name__ == "__main__":
    main()
