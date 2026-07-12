---
title: Refund issue routing decision tree
authority: INTERNAL_SIMULATED
refundType: ANY
topic: DECISION_TREE
stateCode: null
status: null
audience: SYSTEM
simulatedInternalContent: true
sourceUrl: 
lastReviewed: 2026-07-09
---

# Refund issue routing decision tree

1. Determine taxpayer type.
2. Determine authority.
3. Route by status: REJECTED, FLAGGED/ACTION_REQUIRED, PROCESSING/UNDER_REVIEW, SENT, or REDUCED.
4. Apply metadata filters before vector search.
5. If no authoritative source is retrieved, return a safe escalation response.
