# Phase 23 Spec

## Scope

Valid eligible HTTP single-send requests must return one stable `messageId`. Invalid authentication, field, eligibility, routing, rate, or billing boundaries must not create unauthorized tasks, send intents, or charges.

## Owned obligations

- OBL-F-6-1-A
- OBL-F-6-1-B
- OBL-F-6-1-C
- OBL-F-6-4-A
- OBL-F-6-4-B
- OBL-HTTP-IDEMPOTENCY-001
- OBL-FIELD-HTTP-PHONE
- OBL-FIELD-HTTP-TEMPLATE
- OBL-FIELD-HTTP-SIGNATURE
- OBL-FIELD-HTTP-PARAMS

## Non-goals

- Provider HTTP delivery.
- Receipt/final-state closure.
- CMPP acceptance.
- Batch/scheduled sending.
- Tenant console JWT send parity.
