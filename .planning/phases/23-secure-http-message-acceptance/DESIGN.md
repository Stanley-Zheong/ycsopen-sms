# Phase 23 Design

## Runtime path

1. `HmacSmsAuthenticationFilter` reads the exact request body and wraps it for downstream reuse.
2. `HmacRequestAuthenticator` validates App Key, timestamp, nonce, IP allow-list, protected App Secret, and HMAC.
3. `MessageController` enforces API-key rate limits.
4. `MessageSubmitService` claims `submitId`, runs tenant/template/signature/routing checks, persists the protected message task, reserves billing, writes send intent, marks submission accepted, and returns `messageId`.

## Idempotency

`message_submissions(tenant_id, submit_id)` is the unique retry boundary. A duplicate with the same request digest returns the existing task response; a duplicate with a different digest returns `IDEMPOTENCY_CONFLICT`.

## Side-effect boundary

Task persistence, billing reserve, outbox intent, and submission accepted-state all execute inside the existing send transaction.
