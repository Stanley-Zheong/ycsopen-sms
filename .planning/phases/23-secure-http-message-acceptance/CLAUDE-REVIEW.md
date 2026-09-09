# Claude Review

Status: NOT COMPLETED BY CLAUDE CLI.

Claude CLI was available and authenticated, but two tool-less `claude -p` review attempts did not return:

- full staged Phase23 diff excluding evidence;
- reduced code-only diff covering `core`, `web`, and API docs.

Both attempts were interrupted after repeated no-output waits to avoid blocking phase execution indefinitely.

Fallback review performed locally:

- HMAC filter verifies exact request body before controller processing and replays cached body to `@RequestBody`.
- Protected App Secret is read only through `TenantCredentialSecretProtectionService.reveal(...)` with the same context used by creation.
- Redis nonce is used when `StringRedisTemplate` is wired; in-memory fallback remains only for narrow unit tests.
- `submitId` idempotency uses `(tenant_id, submit_id)` uniqueness and request digest conflict detection.
- Accepted submission, protected task, billing reserve, send intent, and accepted status are in one transaction.
- Duplicate same-payload submission returns existing task response before routing, persistence, billing, or outbox mutation.
- Local review finding fixed: `submitId` is now trimmed before length/pattern validation and covered by regression test.

Local review result: NO KNOWN BLOCKING/HIGH FINDINGS.
