# Phase 23 TODO

- [x] OBL-F-6-1-A — REST single-send accepts declared phone, template, signature, variables, and HTTPS callback field.
- [x] OBL-F-6-1-B — successful acceptance returns stable message ID only after submission, task, outbox intent, and entitlement reserve are durable in one transaction.
- [x] OBL-F-6-1-C — rejected/auth/internal boundaries return standard response and avoid task/send/charge leakage.
- [x] OBL-F-6-4-A — HMAC-SHA256 covers method, URI, query, canonical headers, and exact body with protected App Secret.
- [x] OBL-F-6-4-B — timestamp skew, reused nonce, altered body, revoked key, and disallowed IP fail before business processing.
- [x] OBL-HTTP-IDEMPOTENCY-001 — same tenant `submitId` retry returns original result and cannot duplicate task, send intent, quota use, reserve, or charge.
- [x] OBL-FIELD-HTTP-PHONE — phone number is required and matches the domestic mobile rule.
- [x] OBL-FIELD-HTTP-TEMPLATE — template ID is required and validated through tenant-owned approved template compliance.
- [x] OBL-FIELD-HTTP-SIGNATURE — signature ID/default signature is validated through tenant-owned approved signature compliance.
- [x] OBL-FIELD-HTTP-PARAMS — template params are validated exactly by the shared compliance service.
