# ai-service

Stateless prediction + RAG guidance. Internal only — not called directly by the client, only
by refund-service. Doesn't know who's asking, only a form type, jurisdiction, and IRS status.

Port: **8083** · no auth (internal-only, not exposed to the client)

## Responsibilities

- **Prediction**: estimates refund timing from a deterministic rules engine keyed on
  form type / jurisdiction / IRS status (`RulesEngineRefundPredictor`).
- **Guidance**: retrieves the top pre-ranked docs for a `situation_key` (deterministic lookup
  against `refund_guidance_situations`, no live vector search at request time — ranking is
  precomputed offline, see [`ml/rag/`](../ml/rag)), then rewrites them into one plain-English
  paragraph using a local **Ollama** model (`llama3.2:3b`), with a safe fallback to plain
  concatenation if Ollama is slow, down, or returns nothing usable.
- Both are pure lookups — same inputs, same output, no session state.

## API

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/predictions?formType=&jurisdiction=&irsStatus=` | Refund-timing estimate + confidence |
| GET | `/api/v1/guidance?formType=&jurisdiction=&irsStatus=` | Narrative guidance + source docs |

Both return `204 No Content` when nothing applies (e.g. status already `DEPOSITED`).

## Config (`application.yml`)

| Key | Purpose |
|---|---|
| `ollama.base-url` / `ollama.model` | Local Ollama endpoint + model for narrative generation |
| `spring.datasource.*` | Postgres + pgvector (`refund_guidance_docs`, shared with auth/taxpayer-service) |

## Run

```bash
ollama pull llama3.2:3b && ollama serve   # if not already running
./gradlew :ai-service:bootRun
```

Guidance responses still work without Ollama running — they just fall back to concatenated
doc text instead of a generated paragraph.

## Test

```bash
./gradlew :ai-service:test   # 98% JaCoCo gate
```
