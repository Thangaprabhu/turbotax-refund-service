# taxpayer-service

Owns taxpayer identity and access control — the single source of truth for "can this user
see this taxpayer?"

Port: **8082**

## Responsibilities

- Creates/reads taxpayer records (`taxpayers` table, Postgres) — SSN/EIN is encrypted with
  **AWS KMS** before storage (`PiiEncryptionService`, AES via the `alias/turbotax-pii` key)
  and never persisted in plaintext; a SHA-256 hash of the raw value is stored alongside it
  for lookups, since the ciphertext isn't queryable.
- Tracks which users can access which taxpayers (`user_taxpayer_access`), scoping every read
  to the authenticated caller.
- Consumed by refund-service over HTTP to resolve access before returning filing data.

## API

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/taxpayers` | Create a taxpayer under the authenticated user |
| GET | `/api/v1/taxpayers` | List taxpayers the caller has access to (paginated, `page`/`size`, default size 10) |
| GET | `/api/v1/taxpayers/{taxpayerId}` | Fetch one taxpayer, if the caller has access |

Auth: expects a JWT (validated locally against the same signing key auth-service uses).

## Config (`application.yml`)

| Key | Purpose |
|---|---|
| `aws.secrets.jwt-secret-name` | Secrets Manager entry for JWT signature validation |
| `aws.kms.pii-key-alias` | KMS key alias used to encrypt/decrypt SSN/EIN |
| `spring.datasource.*` | Postgres connection (shared with auth-service and ai-service) |

## Run

```bash
./gradlew :taxpayer-service:bootRun
```

Like auth-service, this hits real AWS (Secrets Manager + KMS) unless you override the
endpoint/credentials in `AwsConfig` — no local emulator is wired up yet.

## Test

```bash
./gradlew :taxpayer-service:test   # 98% JaCoCo gate
```
