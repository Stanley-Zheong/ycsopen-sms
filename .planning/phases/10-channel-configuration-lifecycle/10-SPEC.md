# channel-configuration-lifecycle: Channel configuration lifecycle

## Intent

Platform operators manage complete, protected upstream channel configurations;
only validated versions become effective, and a channel cannot be taken offline
while declared dependencies remain unresolved.

## Scope

### In

- Protocol, carrier, endpoint, SP/service identifiers, source number,
  credential, connection/window, price, priority, availability, extension,
  status, pause, and recovery configuration.
- Protocol-specific validation and connectivity conformance adapter checks.
- Protected credential storage, immutable configuration versions, atomic hot
  activation, explicit rollback, and safe activation evidence.
- Dependency inventory and destination migration before offline/delete.
- One Admin production page at `/admin/channel/configuration` with list,
  detail/edit form, connectivity test, activation result, dependency preview,
  migration wizard, and offline confirmation.

### Out

- Health scoring/heartbeats, channel pools, routing policy, durable task
  migration, provider status taxonomy, and real CMPP/SGIP/SMGP/HTTP sessions;
  these belong to the owning later phases in the roadmap.
- Mobile UI and all non-Chrome browser support.

## External behavior

### channel-configuration-lifecycle-01

Where an operator creates or edits a channel, when protocol, endpoint,
credential, connection, pricing, priority, and availability values are
submitted, the service shall validate the protocol-specific contract and
persist protected configuration data without returning plaintext credentials.

### channel-configuration-lifecycle-02

Where a complete channel configuration is submitted for activation, when its
connectivity conformance check succeeds, the service shall create an immutable
version, atomically hot-load it without restart, and identify that version to
consumers; a failed load shall preserve the prior effective version and expose
an auditable safe reason with an explicit retry path.

### channel-configuration-lifecycle-03

Where an operator requests offline or delete, when the channel is referenced by
a declared route, pool, filing, price, or active task dependency, the service
shall return a complete dependency inventory and block mutation until every
reference is migrated or explicitly resolved; only then may the channel become
offline/deleted.

### channel-configuration-lifecycle-04

Where an operator opens the Admin configuration route in desktop Chrome, the
React page shall render documented rows, fields, dialogs, feedback, and
permission states with stable test IDs and immediate persisted-result feedback.

## Errors and boundaries

| Case | Required outcome | Behavior ID |
| --- | --- | --- |
| Missing name, unsupported protocol, invalid endpoint, connection, price, priority, or availability | Validation response and no version/effective-state change | channel-configuration-lifecycle-01 |
| Duplicate channel name | Conflict response and no partial write | channel-configuration-lifecycle-01 |
| Credential absent or protection failure | Safe validation/protection error; no plaintext log or response | channel-configuration-lifecycle-01 |
| Connectivity adapter rejects protocol-specific values | Activation remains non-effective with safe reason | channel-configuration-lifecycle-01, channel-configuration-lifecycle-02 |
| Concurrent activation or stale expected version | One guarded activation wins; stale attempt retains current version and is retryable | channel-configuration-lifecycle-02 |
| Hot-load failure | Prior effective version remains active; audit/result exposes reason code only | channel-configuration-lifecycle-02 |
| Offline/delete with unresolved dependency | Inventory returned; no offline/delete mutation | channel-configuration-lifecycle-03 |
| Offline/delete after all destinations resolved | Status becomes OFFLINE with actor/time evidence; repeat is idempotent | channel-configuration-lifecycle-03 |
| Missing operator permission | 403 or explicit denied state with no configuration data | channel-configuration-lifecycle-01, channel-configuration-lifecycle-04 |

## Verification

| Verification ID | Evidence |
| --- | --- |
| T-P10-CONFIG | Focused validation and service/controller tests cover all PRD channel fields, protocol rules, protection, and redaction. |
| T-P10-SCHEMA | MySQL migration test proves encrypted columns, precision, version immutability, and complete persisted state. |
| T-P10-HOTLOAD | Integration/fault test proves no-restart activation, consumer version identity, failed-load rollback, audit, and retry. |
| T-P10-DEPENDENCY | Dependency integration test proves inventory and migration gating across current reference tables and active tasks. |
| T-P10-UI-CHROME | Real installed Chrome at 1440x900 exercises channel creation, connectivity/activation result, dependency preview/migration, and offline result. |
| T-P10-TRACE | 17 atomic evidence files, exact UI/test matrix, production UI validator, and empty scoped TODO. |

## Requirement trace

| PRD requirement | Behavior IDs | Verification IDs |
| --- | --- | --- |
| REQ-F-4-1 | channel-configuration-lifecycle-01, channel-configuration-lifecycle-04 | T-P10-CONFIG, T-P10-SCHEMA, T-P10-UI-CHROME |
| REQ-F-4-2 | channel-configuration-lifecycle-02 | T-P10-HOTLOAD |
| REQ-F-4-4 | channel-configuration-lifecycle-03, channel-configuration-lifecycle-04 | T-P10-DEPENDENCY, T-P10-UI-CHROME |

## Owned obligations

- OBL-F-4-1-A
- OBL-F-4-1-B
- OBL-F-4-1-C
- OBL-F-4-2-A
- OBL-F-4-2-B
- OBL-F-4-4-A
- OBL-F-4-4-B
- OBL-FIELD-CHANNEL-NAME
- OBL-FIELD-CHANNEL-PROTOCOL
- OBL-FIELD-CHANNEL-ENDPOINT
- OBL-FIELD-CHANNEL-CREDENTIAL
- OBL-FIELD-CHANNEL-CONNECTION
- OBL-FIELD-CHANNEL-PRICE
- OBL-FIELD-CHANNEL-PRIORITY
- OBL-STATE-CHANNEL-OFFLINE
- OBL-DATA-10-4-CHANNEL
