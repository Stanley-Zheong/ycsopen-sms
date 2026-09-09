# Phase 28 Spec

## Outcomes

1. Tenants can configure distinct HTTPS status, uplink, and unsubscribe callback URLs with retry policy.
2. Callback destinations reject non-HTTPS, localhost, and private-network targets before save/test/enqueue.
3. Status events are wrapped in a versioned signed envelope with a stable logical idempotency ID.
4. Delivery attempts are recorded; failed delivery retries to the configured terminal attempt count and then becomes visible as `PUSH_FAILED`.
5. Platform operators can list failed pushes, replay, pause, and resume without changing another tenant's destination.

## Boundaries

- No Edge/Safari/mobile/browser-matrix verification.
- No automatic external scheduler is introduced; `deliverNext()` is the reusable dispatch primitive for workers.
- No uplink content normalization or unsubscribe policy implementation in this phase.
