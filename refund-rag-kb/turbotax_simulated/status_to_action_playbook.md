---
id: tt_sim_status_to_action_playbook
source_type: simulated_internal_playbook
authority: TURBOTAX_SIMULATED
refund_type: ANY
state_code: null
topic: STATUS_TO_ACTION
statuses: [RECEIVED, APPROVED, SENT, DEPOSITED, FLAGGED, UNDER_REVIEW, DELAYED, REJECTED]
last_reviewed: 2026-07-08
source_url: simulated_internal_demo
---

# Simulated Status-to-Action Playbook

## RECEIVED

Your return has been received. No action is usually required. Check again after the next update window.

## APPROVED

Your refund has been approved. Review the expected payment date and delivery method.

## SENT

Your refund was sent. Check bank account or mailbox depending on delivery method. Banks and mail delivery may add time.

## DEPOSITED

Your refund appears complete. If the amount differs from expected, review adjustment notices.

## FLAGGED

A potential issue requires review. Check for notices and identity verification requests. Do not upload documents unless the app provides a secure authenticated flow.

## UNDER_REVIEW

The authority is reviewing the return. No action may be required unless a notice requests information.

## DELAYED

Show the likely delay reason if known. If no reason is known, suggest monitoring status and checking for notices.

## REJECTED

Explain the rejection reason and guide the user to correct and resubmit if applicable.
