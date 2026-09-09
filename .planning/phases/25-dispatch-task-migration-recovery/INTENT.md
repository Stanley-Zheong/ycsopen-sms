# Phase 25 Intent

Operators need a safe way to handle work already attached to a channel that is paused or failing. The intent is to prevent new duplicate contacts while giving operators explicit, auditable choices:

- Migrate work that has not been submitted upstream.
- Retry work only after it is already failed.
- Quarantine uncertain upstream outcomes.
- Restore a paused channel only after a successful recovery test.

The implementation is intentionally narrow and reuses existing channel health, outbox, protected mobile, and route-candidate boundaries.
