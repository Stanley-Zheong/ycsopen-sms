# Phase 28 Context

Package: `webhook-delivery-transport`

Phase28 delivers tenant Webhook callback configuration and the reusable signed delivery transport for status/uplink/unsubscribe-style events. It depends on the HTTP acceptance/delivery data already created by Phases 23-24 and the admin operations conventions from Phase27.

Scope is intentionally limited to callback configuration, signed/idempotent event enqueue, retry/failure evidence, and admin replay/pause/resume. Domain-specific uplink normalization and unsubscribe policy remain later phase work.
