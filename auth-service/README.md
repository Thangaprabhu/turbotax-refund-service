# auth-service

Owns identity. The only service that ever holds a password hash or signs a JWT.

Port: **8081**

## Responsibilities

- Registers users, hashes passwords (BCrypt), stores them in `users` (Postgres).
- Authenticates login and issues JWTs signed with a key pulled from **AWS Secrets Manager**
  (`dev/turbotax/jwt`, KMS-encrypted at rest) — never a key on disk or in config.
- Every other service independently validates the JWT signature; this service is not a
  runtime dependency for auth checks elsewhere, only for issuing tokens.

## API

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/auth/register` | Create a user account |
| POST | `/api/v1/auth/login` | Authenticate, returns a JWT |

## Config (`application.yml`)

| Key | Purpose |
|---|---|
| `aws.secrets.jwt-secret-name` | Secrets Manager entry holding the JWT signing keypair |
| `jwt.expiry-minutes` | Token TTL (default 480 = 8h) |
| `spring.datasource.*` | Postgres connection (`turbotax` DB, shared with taxpayer-service and ai-service) |

## Run

```bash
./gradlew :auth-service:bootRun
```

Requires Postgres up (`docker compose up -d postgres`) and a `dev/turbotax/jwt` secret
(JSON with `private_key`/`public_key` PEM strings) present in **AWS Secrets Manager** under
the account/region your credentials resolve to — there's no local emulator wired up for this
yet, so this hits real AWS. Override `aws.endpoint`/`aws.access-key`/`aws.secret-key` (see
`AwsConfig`) if you point it at LocalStack instead. Startup fails fast if the secret can't be
loaded (see `SecretsManagerService`).

## Test

```bash
./gradlew :auth-service:test   # 98% JaCoCo gate
```

## Key terms

- **BCrypt** — adaptive password-hashing algorithm with a built-in salt. Passwords are hashed
  with it before being stored in `users`; the raw password is never persisted or logged.
- **JWT (JSON Web Token)** — a signed, self-contained token this service issues on
  register/login. It carries the user id and an expiry, and every other service validates its
  signature locally rather than calling back here to check it.
- **AWS Secrets Manager** — where the JWT signing keypair (`dev/turbotax/jwt`) actually lives,
  instead of a config file or environment variable. Fetched once at startup and cached in
  memory (`SecretsManagerService`) — a failure here means the service refuses to start.
- **KMS (Key Management Service)** — the AWS encryption-key service Secrets Manager uses to
  encrypt that JWT keypair at rest. auth-service doesn't call KMS directly; it's what protects
  the secret it reads.
- **Bearer token** — the `Authorization: Bearer <jwt>` header convention. Every other service
  in this repo forwards or validates this header rather than re-authenticating the user.
