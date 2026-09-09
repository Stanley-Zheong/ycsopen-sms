# Phase 24 Decisions

- D24-01: Phase24 follows owner obligations only. F-6.3/F-7.1 text remains roadmap context, but catalog ownership places those UI/status operation obligations in `message-receipt-error-operations`.
- D24-02: Unknown upstream outcome is not retried automatically. The outbox remains `CLAIMED` with `UNKNOWN_OUTCOME`-class error detail, preserving no-duplicate safety for Phase25 recovery.
- D24-03: Recipient reveal is allowed only inside `MessageTaskProtectionAdapter.revealMobileForDispatch`; status APIs return message/provider/submission/billing trace but never mobile.
- D24-04: Billing confirmation is receipt-driven. Upstream acceptance only moves the task to `SENT`; final delivered/failed receipt confirms or reverses billing.
