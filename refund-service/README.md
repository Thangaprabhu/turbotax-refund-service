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

## API workflow steps

<details>
<summary><strong>Create filing</strong> — <code>POST /api/v1/taxpayers/{taxpayerId}/filings</code></summary>

1. Client → **refund-service**
2. **Access check**: refund-service → **taxpayer-service** `GET /api/v1/taxpayers/{taxpayerId}`,
   forwarding the caller's bearer token — that endpoint only returns the taxpayer if the
   authenticated user actually has access to it (via `user_taxpayer_access`), otherwise it
   errors. refund-service never makes this decision itself.
3. Checks DynamoDB for an existing filing with the same year/form/jurisdiction — `409` if found
4. refund-service → **ai-service** `/predictions` for a refund-timing estimate
5. Saves the filing to DynamoDB (`status = RECEIVED`), evicts that taxpayer's filings entry
   from the Redis cache
6. Publishes a `filing.created` event to Kafka
7. Returns the created filing
</details>

<details>
<summary><strong>List / get filings</strong> — <code>GET .../filings</code>, <code>/latest</code>, <code>/{taxYear}/{formType}/{jurisdiction}</code></summary>

1. Client → **refund-service**
2. **Access check** (same as above) → **taxpayer-service**
3. Reads the taxpayer's filings from Redis (4h TTL) if cached, else DynamoDB — list/latest/by-year
   all read from the same cached collection; pagination is sliced in memory, not re-queried
4. Any filing with a missing or stale prediction is backfilled with a live call to
   **ai-service** `/predictions`, and the correction is persisted back to DynamoDB
5. Returns the filing(s)
</details>

<details>
<summary><strong>Get guidance for a filing</strong> — <code>GET .../{taxYear}/{formType}/{jurisdiction}/guidance</code></summary>

1. Client → **refund-service**
2. Resolves the filing (same access check + cache/backfill as list/get above)
3. refund-service → **ai-service** `/guidance` with the filing's form type, jurisdiction, and status
4. ai-service resolves a `situation_key`, looks up its precomputed top doc IDs, fetches doc
   content from Postgres/pgvector, and rewrites it into one paragraph via Ollama (or falls back
   to plain concatenation)
5. Returns `204` if the filing's status doesn't need guidance (e.g. `RECEIVED`, `DEPOSITED`),
   otherwise the narrative + source docs
</details>

<details>
<summary><strong>Update filing status</strong> — <code>PATCH .../{sk}/status</code></summary>

1. Client → **refund-service**
2. **Access check** (same as above) → **taxpayer-service**
3. Loads the filing from DynamoDB, applies the new status
4. refund-service → **ai-service** `/predictions` for a refreshed estimate
5. Saves to DynamoDB, evicts the Redis cache entry for that taxpayer
6. Publishes a `refund.status.updated` event to Kafka (old status → new status)
7. Returns the updated filing
</details>

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
