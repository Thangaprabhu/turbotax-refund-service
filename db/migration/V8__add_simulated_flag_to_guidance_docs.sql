-- Marks demo-authored support-playbook/FAQ content (no real source_url) as distinct from
-- actual IRS/state guidance, so the API and UI never present the two as the same kind of
-- source. See ml/rag/knowledge_base.py's module docstring for the full rationale.

ALTER TABLE refund_guidance_docs
    ADD COLUMN simulated_internal_content BOOLEAN NOT NULL DEFAULT FALSE;
