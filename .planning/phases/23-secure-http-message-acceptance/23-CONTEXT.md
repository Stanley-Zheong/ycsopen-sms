# Phase 23 Context

Package: `secure-http-message-acceptance`

This phase owns the HTTP single-send acceptance boundary only:

- `POST /api/v1/sms/send`
- App Key/App Secret HMAC authentication
- timestamp, nonce, IP allow-list, and standard error response
- tenant/resource/routing/rate/billing orchestration through existing services
- business `submitId` idempotency
- durable accepted task and send-intent outbox

It deliberately excludes provider dispatch, receipts, status closure, CMPP, batch, and tenant console parity.
