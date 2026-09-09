# Phase 27 Decisions

- Keep Phase27 as an operations layer over existing accepted/delivered message data. Do not duplicate the acceptance, dispatch, or receipt pipeline.
- Use `message_operation_events.operation_key` for action idempotency across resend, appeal, receipt correction/replay, bulk marking, and export handoff.
- Return protected phone fields as `已保护`; Phase27 does not reveal or decrypt phone numbers.
- Export remains a request handoff to the future `secure-async-export` owner; no export file is generated here.
- Browser validation remains local Google Chrome only.
