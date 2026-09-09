# Phase 28 Decisions

- Keep Webhook transport generic: domain phases enqueue events, this phase signs, stores, retries, and exposes operations.
- Use `tenant_id + event_type + logical_id` as the event idempotency boundary.
- Manual replay never accepts a replacement destination; it reuses the stored event destination to prevent cross-tenant redirect.
- Browser validation remains local Google Chrome only.
- Webhook signatures use a generated tenant secret stored on `tenant_callback_configs`; no source-code-derived shared signing seed is used.
- Delivery HTTP calls are not wrapped by the service-level transactional methods, and the concrete HTTP client sets connect/read timeouts plus disables redirects.
- SSRF checks resolve the host and reject loopback, private, link-local, multicast, any-local, localhost, and `.local` destinations; delivery re-validates immediately before each outbound call.
