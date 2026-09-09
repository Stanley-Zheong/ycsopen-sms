# Phase 25 Context

Package: `dispatch-task-migration-recovery`

This phase closes the durable dispatch recovery slice after HTTP acceptance and upstream delivery exist. It focuses on paused-channel task inventory, READY/PENDING migration, FAILED retry, uncertain provider outcome quarantine, no-backup evidence, and recovery-test-gated channel resume.

Scoped owner obligations:

- OBL-F-4-7-C
- OBL-F-4-7-D
- OBL-F-5-10-B
- OBL-STATE-CHANNEL-RECOVER
- OBL-STATE-MESSAGE-RETRY
- OBL-EDGE-UPSTREAM-OUTAGE

Dependencies used:

- Phase 11 channel health/pause UI and channel state model.
- Phase 21 route candidate and channel routability model.
- Phase 23 accepted message storage and idempotency boundary.
- Phase 24 HTTP dispatch outbox and protected recipient reveal boundary.

Non-goals:

- No browser matrix beyond local Chrome.
- No mobile app behavior.
- No new routing-rule editor.
- No new transport protocol.
