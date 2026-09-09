# Phase 23 Iterations

- Initial inspection found existing send orchestration but incomplete HMAC body/IP/secret validation and no `submitId` idempotency.
- Added complete HMAC filter/authenticator while keeping interceptor as compatibility pass-through.
- Added idempotency service and `message_send_outbox` intent table.
- First targeted test run exposed test-only Mockito nesting and H2 database reuse issues; fixed tests without changing production logic.
- Local diff review found `submitId` trim/length validation order was inconsistent; fixed and added a regression test.
- Full backend test suite passed after implementation.
