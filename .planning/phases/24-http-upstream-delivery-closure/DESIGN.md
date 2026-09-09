# Phase 24 Design

Core components:

- `SmsUpstreamProviderClient`: narrow provider submit SPI.
- `HttpSmsUpstreamProviderClient`: configured HTTP transport using `Idempotency-Key`.
- `SandboxSmsUpstreamProviderClient`: deterministic local fallback when no real provider is configured.
- `HttpMessageDeliveryService`: claims outbox rows, sends provider request, applies provider response and receipts.
- `MessageStatusQueryService`: tenant-scoped safe trace query.
- `ProtectedMessageDispatchRecipientResolver`: dispatch-only bridge from task id to protected mobile reveal.

State transitions:

- `READY outbox + PENDING task -> CLAIMED -> SENT outbox + SENT task`
- `READY outbox + PENDING task -> CLAIMED -> FAILED outbox + FAILED task`
- `READY outbox + PENDING task -> CLAIMED with UNKNOWN_OUTCOME; task stays PENDING`
- `SENT task + delivered receipt -> DELIVERED task + CONFIRMED billing`
- `SENT task + failed receipt -> FAILED task + REVERSED billing`

No UI is implemented in this phase.
