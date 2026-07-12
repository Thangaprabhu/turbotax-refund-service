-- RAG knowledge base for "what to do about my refund issue" guidance.
-- See docs/ai-refund-prediction-scope.md (Option D) and ml/rag/.
--
-- Retrieval is precomputed offline (ml/rag/build_knowledge_base.py) rather
-- than computed live in the app, since the set of situations (status x
-- individual/business x federal/state) is small and enumerable -- the same
-- reasoning that kept rules-v1 out of a live ML call. top_doc_ids is the
-- result of a real pgvector cosine-similarity search at ingestion time.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE refund_guidance_docs (
    id BIGSERIAL PRIMARY KEY,
    topic VARCHAR(64) NOT NULL,
    content TEXT NOT NULL,
    source_url VARCHAR(512),
    embedding vector(384) NOT NULL
);

-- Not load-bearing at this corpus size (a few dozen rows) -- included to
-- reflect the real pattern; a brute-force scan would be just as fast here.
CREATE INDEX refund_guidance_docs_embedding_idx
    ON refund_guidance_docs USING hnsw (embedding vector_cosine_ops);

CREATE TABLE refund_guidance_situations (
    situation_key VARCHAR(64) PRIMARY KEY,
    description TEXT NOT NULL,
    embedding vector(384) NOT NULL,
    top_doc_ids BIGINT[] NOT NULL
);
