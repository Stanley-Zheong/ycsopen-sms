# Phase 24 TODO

- [x] Owner obligation set identified by `validate-prd-obligations --owner http-upstream-delivery-closure`.
- [x] Durable dispatch claiming prevents duplicate provider sends from the same ready outbox row.
- [x] HTTP connector sends idempotency-keyed payload to a local sandbox provider.
- [x] Unknown provider outcome is quarantined without automatic duplicate retry.
- [x] Provider rejection fails task and reverses billing once.
- [x] Delivered receipt confirms billing once.
- [x] Failed receipt reverses billing once.
- [x] Duplicate receipt stores one receipt and does not repeat financial effects.
- [x] Status query is tenant-scoped and does not expose protected mobile.
- [x] Complete verification evidence is captured.
- [x] Phase review is captured.
- [x] Phase changes are ready for commit and push.
