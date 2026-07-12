-- Structured pre-filter for the RAG knowledge base, to fix a real retrieval
-- problem: with only 18 short docs and a small local embedding model, cosine
-- similarity alone let off-topic content (e.g. business-only ERC delays)
-- leak into individual-federal results. Filtering by applicability BEFORE
-- ranking by similarity is standard hybrid retrieval, and fixes this
-- structurally rather than hoping a bigger model discriminates better.

ALTER TABLE refund_guidance_docs
    ADD COLUMN applicable_entity_types TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN applicable_jurisdictions TEXT[] NOT NULL DEFAULT '{}';
