# refund-service

Owns filings and refund status. Every filing read/write starts here and fans out to
taxpayer-service (access check) and ai-service (prediction + guidance).

Port: **8080**

## Responsibilities

- Creates and tracks filings in **DynamoDB** (`turbotax-filings`, one partition per taxpayer)
  — the only service with its own fully isolated datastore.
- Publishes `filing.created` and `refund.status.updated` events to **Kafka** (2 topics).
- Caches filing reads in **Redis** (4h TTL, evicted on every write — not TTL-raced).
- Calls taxpayer-service to verify the caller has access to the taxpayer before returning
  anything, and ai-service for refund-timing predictions + RAG guidance.

## API

All routes are nested under a taxpayer: `/api/v1/taxpayers/{taxpayerId}/filings`

| Method | Path | Description |
|---|---|---|
| POST | `` | Create a filing (triggers IRS status polling) |
| GET | `` | List filings for a taxpayer, paginated (`page`/`size`, default size 10) |
| GET | `/latest` | Most recent filing + refund status |
| GET | `/{taxYear}/{formType}/{jurisdiction}` | Refund status for a specific filing |
| GET | `/{taxYear}/{formType}/{jurisdiction}/guidance` | RAG-backed guidance for that filing's status (proxies ai-service) |
| PATCH | `/{sk}/status` | Update IRS status on a filing |

Auth: every route takes the raw `Authorization` bearer token and forwards it to
taxpayer-service for the access check — this service doesn't validate the JWT itself.

## Config (`application.yml`)

| Key | Purpose |
|---|---|
| `dynamodb.endpoint` | Must point at local DynamoDB (`http://localhost:8000`) — leaving it blank sends traffic to real AWS |
| `services.taxpayer-service.base-url` | Where to call for access checks |
| `services.ai-service.base-url` | Where to call for predictions/guidance |
| `spring.kafka.bootstrap-servers` | Kafka broker |
| `spring.data.redis.*` / `cache.ttl.filings-hours` | Cache config |

## Run

```bash
docker compose up -d dynamodb-local redis kafka
./gradlew :refund-service:bootRun
```

## Test

```bash
./gradlew :refund-service:test   # 98% JaCoCo gate
```

## Key terms

- **DynamoDB** — AWS's managed NoSQL key-value store. Filings are stored one partition per
  taxpayer (`taxpayerId` as partition key, a composite `sk` of year#form#jurisdiction as sort
  key), so a taxpayer's full filing history is always a single fast query.
- **Redis** — an in-memory cache sitting in front of DynamoDB reads, with a 4-hour TTL. Writes
  (create/update) explicitly evict the cache entry rather than waiting for it to expire, so a
  status update is never masked by stale cached data.
- **Kafka** — an append-only event log. This service publishes `filing.created` and
  `refund.status.updated` to it without knowing or caring who (if anyone) is consuming them —
  decoupling this service from whatever reacts to those events.
- **Virtual threads** (Java 21, `spring.threads.virtual.enabled`) — lightweight threads that
  let blocking calls (to taxpayer-service, ai-service, DynamoDB) not tie up a limited OS-thread
  pool under load, without rewriting the code to be reactive/async.
- **Bearer token forwarding** — this service never validates the JWT itself; it forwards the
  caller's raw `Authorization` header to taxpayer-service on every request and trusts its
  access decision.
