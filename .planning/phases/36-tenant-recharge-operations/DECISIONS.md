# Phase 36 Decisions

- Reuse Phase 22 prepaid account and balance audit tables instead of introducing a new wallet ledger.
- Store transaction reference as hash plus mask, not plaintext.
- Use synchronous finance review crediting; no payment gateway callback in this phase.
- Reuse `trial-prepaid:read/write` permissions because recharge is part of the same finance/prepaid surface.
- Use direct inline approve/reject actions instead of modal review flow.
