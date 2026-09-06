# Platform system-message bootstrap

## Intent

Build a deterministic platform-notification bootstrap path for registration and operational messages before tenant acceptance/routing channels exist.

## Scope

### In
- Platform registration/provider bootstrap adapter.
- Controlled delivery template and recipient policy.
- Delivery result normalization and durable evidence.
- Recursion guard and retry/error classification.

### Out
- Tenant acceptance flows, channel configuration/routing, tenant templates, billing, and generic business alerts.

## External behavior

### platform-system-message-bootstrap-01

Where platform bootstrap credentials are provisioned and a bootstrap provider is enabled, when registration/operational notification is emitted, the platform bootstrap SPI must send it to an authoritative provider sandbox and persist provider acceptance/failure as evidence.

### platform-system-message-bootstrap-02

Where provider dispatch fails, when retry/guard logic decides whether to retry, when recursion condition triggers, or when terminal failure is determined, the module must produce deterministic outcomes and preserve attribution.

### platform-system-message-bootstrap-03

Where message templates, recipients, and delivery results are present, when admin/monitoring consumes notification evidence, no delivery secrets must be exposed and each delivery is auditable with provider result and purpose actor.

## Internal behavior

### platform-system-message-bootstrap-01

Platform bootstrap dispatch is implemented as a bounded SPI adapter over explicit provider settings, with explicit success/failure contracts and strict template/recipient policy.

### platform-system-message-bootstrap-02

Delivery execution records terminal status, retry policy decision, guard reason, and idempotent signature so notification attempts cannot recursively trigger the same bootstrap message.

### platform-system-message-bootstrap-03

Audit logging for bootstrap notifications uses normalized fields only (purpose, actor, result class, error class, correlation) and redacts transport/provider secrets.

## Errors and boundaries

| Case | Required outcome | Behavior ID |
| --- | --- | --- |
| Provider sandbox unreachable | Return terminal failure with bounded retry guidance; no silent success | platform-system-message-bootstrap-02 |
| Delivery recursion attempt | Do not re-enter the same bootstrap dispatch path | platform-system-message-bootstrap-02 |
| Delivery success but persistence lag | Keep evidence durable with delivery correlation and result state | platform-system-message-bootstrap-01 |

## Verification

### platform-system-message-bootstrap-01

Where sandbox credentials and template policy are ready, when bootstrap is exercised, the verification must prove one real end-to-end provider hit and one durable evidence record.

### platform-system-message-bootstrap-02

Where dispatch failure and repeat-event scenarios are simulated, when retry guard is applied, the suite must prove bounded behavior and no recursive re-entry.

### platform-system-message-bootstrap-03

Where delivery evidence is generated, when audit queries are read, the suite must show no secret material in persisted logs and stable template/recipient identity.

## Requirement trace

| PRD requirement | Behavior IDs | Verification IDs |\n+| --- | --- | --- |
| PRD F-2.1, PLAN-REVIEW PR-006 | platform-system-message-bootstrap-01 | platform-system-message-bootstrap-01 |
| REQ-F-12-2, PLAN-REVIEW PR-006 | platform-system-message-bootstrap-02 | platform-system-message-bootstrap-02 |
| PROJECT-SYSTEM-MESSAGING, PRD F-7.1, F-6.2 | platform-system-message-bootstrap-03 | platform-system-message-bootstrap-03 |

## Owned obligations

- OBL-PLATFORM-MESSAGE-001
- OBL-PLATFORM-MESSAGE-002
- OBL-PLATFORM-MESSAGE-003

