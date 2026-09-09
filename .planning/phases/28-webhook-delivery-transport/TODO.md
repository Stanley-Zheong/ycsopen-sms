# Phase 28 TODO

- [x] OBL-F-6-6-A tenant callback configuration supports separate HTTPS status/uplink/unsubscribe destinations and SSRF-safe test.
- [x] OBL-F-6-6-B signed versioned callbacks carry logical idempotency and retry to terminal attempt count.
- [x] OBL-F-6-6-C authorized manual replay is idempotent, records attempts, and uses the stored tenant destination.
- [x] OBL-F-7-7-A push failures expose destination, reason, attempts, policy, state, and tenant without cross-tenant leakage.
- [x] OBL-F-7-7-B retry threshold, replay, pause, resume, and long-failure state have explicit behavior.
- [x] OBL-FIELD-HTTP-CALLBACK callbackUrl is HTTPS, SSRF-safe, tenant authorized, and defaults to configured destination.
- [x] OBL-EDGE-WEBHOOK-FAILURE five-attempt default failure becomes visible and supports replay.
- [x] OBL-DATA-10-6-CALLBACK data model preserves destinations, retry policy, state, version, and latest failure evidence.
- [x] UI elements, Playwright IDs, and test matrix are documented.
- [x] Final verification evidence is captured.
- [x] Review evidence is captured.
- [x] Phase28 changes are ready for commit and push.
