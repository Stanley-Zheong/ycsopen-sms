# Phase 32 Spec

Owned obligations: OBL-F-7-5-A, OBL-F-7-5-B, OBL-F-7-5-C, OBL-F-10-1-A, OBL-F-10-4-A, OBL-F-10-4-B, OBL-DATA-10-7-UPLINK.

## Required behavior

- OBL-F-7-5-A: admin uplink details expose counts, phone, content keyword, state, carrier, push state, period, tenant, destination, location, channel, and receive time.
- OBL-F-7-5-B: HTTP and CMPP connector calls normalize into one tenant-scoped source-labelled record and can enqueue/replay UPLINK push through generic webhook delivery.
- OBL-F-7-5-C: tenant auto-reply configuration is tenant-scoped, requires audit reason, and the auto-reply decision hook enforces a loop guard.
- OBL-F-10-1-A: operations can search full-platform uplinks by tenant, protected number, time, keyword, carrier, and push state.
- OBL-F-10-4-A: operations can monitor uplink delivery success/failure/retry evidence from real delivery attempt rows.
- OBL-F-10-4-B: operations can pause/resume/replay the exact push event without replacing destination URLs.
- OBL-DATA-10-7-UPLINK: normalized records preserve tenant, protected phone, content, destination, location, channel, receive time, delivery destination, push state, and attempt linkage.

## Out of scope

- Unsubscribe suppression policy.
- Mobile runtime.
- Browser compatibility beyond local Chrome.
- New design system components.
