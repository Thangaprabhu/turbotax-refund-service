# Refund Issue RAG Knowledge Base

Purpose: Curated training/ingestion corpus for a TurboTax-style Refund Issue Assistant.

Use case: Answer: "What actions can the user take if there are issues with their refund?"

Notes:
- Official documents are summarized from public IRS, Taxpayer Advocate Service, California FTB, and New York DTF sources.
- `turbotax_simulated/` contains simulated internal-help/playbook content for demo purposes only.
- Each Markdown document starts with YAML metadata for filtering during retrieval.
- Suggested chunking: 300-600 tokens, overlap 50-100 tokens.
- Suggested metadata filters: authority, refund_type, state_code, topic, status, source_type.
