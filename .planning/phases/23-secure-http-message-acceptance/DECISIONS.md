# Phase 23 Decisions

- Use `submitId` as the public business idempotency key because PRD 5.15 requires retry by the same business serial number and the schema already contains `message_submissions.submit_id`.
- Keep provider dispatch out of scope. Phase23 writes `message_send_outbox` as a durable intent; Phase24 owns claiming and provider delivery.
- Use Redis-backed nonce recording when Redis is wired; retain in-memory fallback only for narrow unit tests without Redis.
- Keep HMAC body verification in a servlet filter, not the MVC interceptor, so the raw body can be verified and still replayed to `@RequestBody`.
- Preserve existing template, signature, routing, rate-limit, tenant eligibility, and billing services rather than replacing them with a new acceptance stack.
