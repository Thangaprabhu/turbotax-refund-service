"""
Ingests the refund-guidance knowledge base into Postgres/pgvector:
  1. Embeds every doc in knowledge_base.DOCS locally (fastembed, no API key).
  2. Embeds every situation's query description in knowledge_base.SITUATIONS.
  3. For each situation, runs a REAL pgvector cosine-similarity search to find
     the top-k most relevant docs, and stores that result.

Retrieval itself happens here, at ingestion time, not live in the Java app --
see the comment at the top of V6__add_refund_guidance_rag_tables.sql for why
that's the right call given the situation space is small and enumerable.

Run:
    ml/.venv/bin/python ml/rag/build_knowledge_base.py
"""
import os

import psycopg2
from fastembed import TextEmbedding
from pgvector.psycopg2 import register_vector

from knowledge_base import DOCS, SITUATIONS

TOP_K = 4
DB_DSN = os.environ.get(
    "GUIDANCE_DB_DSN",
    "host=localhost port=5432 dbname=turbotax user=turbotax password=turbotax",
)


def connect():
    conn = psycopg2.connect(DB_DSN)
    register_vector(conn)
    return conn


def load_docs(conn, model):
    contents = [d["content"] for d in DOCS]
    embeddings = list(model.embed(contents))

    with conn.cursor() as cur:
        cur.execute("TRUNCATE refund_guidance_situations")
        cur.execute("TRUNCATE refund_guidance_docs RESTART IDENTITY CASCADE")
        for doc, embedding in zip(DOCS, embeddings):
            cur.execute(
                """
                INSERT INTO refund_guidance_docs
                    (topic, content, source_url, embedding, applicable_entity_types, applicable_jurisdictions)
                VALUES (%s, %s, %s, %s, %s, %s)
                """,
                (doc["topic"], doc["content"], doc["source_url"], embedding,
                 doc["entity_types"], doc["jurisdictions"]),
            )
    conn.commit()
    print(f"loaded {len(DOCS)} docs")


def load_situations(conn, model):
    descriptions = [s["description"] for s in SITUATIONS]
    embeddings = list(model.embed(descriptions))

    with conn.cursor() as cur:
        for situation, embedding in zip(SITUATIONS, embeddings):
            # Structured pre-filter (applicability), THEN real pgvector cosine-distance
            # ranking (<=>) among only the candidates that pass the filter.
            cur.execute(
                """
                SELECT id, topic, 1 - (embedding <=> %s) AS similarity
                FROM refund_guidance_docs
                WHERE applicable_entity_types && ARRAY['ANY', %s]
                  AND applicable_jurisdictions && ARRAY['ANY', %s]
                ORDER BY embedding <=> %s
                LIMIT %s
                """,
                (embedding, situation["entity_type"], situation["jurisdiction"], embedding, TOP_K),
            )
            top = cur.fetchall()
            top_doc_ids = [row[0] for row in top]

            cur.execute(
                """
                INSERT INTO refund_guidance_situations (situation_key, description, embedding, top_doc_ids)
                VALUES (%s, %s, %s, %s)
                """,
                (situation["situation_key"], situation["description"], embedding, top_doc_ids),
            )
            topics = [f"{row[1]} ({row[2]:.3f})" for row in top]
            print(f"  {situation['situation_key']:32s} -> {topics}")
    conn.commit()
    print(f"loaded {len(SITUATIONS)} situations")


def main():
    model = TextEmbedding(model_name="BAAI/bge-small-en-v1.5")
    conn = connect()
    try:
        load_docs(conn, model)
        load_situations(conn, model)
    finally:
        conn.close()


if __name__ == "__main__":
    main()
