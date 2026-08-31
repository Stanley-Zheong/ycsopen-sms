# Console design system and prototype foundation - SPEC

## Intent

Build a prototype-only console design and information-architecture foundation before any production business feature implementation.

## Scope

### In
- Deliver Phase 2 UI foundation assets for shared navigation, admin/tenant IA registry, and prototype shell interactions.
- Declare and bind stable routes, selectors, role scopes, and behavior evidence for owned PRD obligations.
- Produce `UI-ELEMENTS.md`, `TEST-MATRIX.md`, `02-UI-SPEC.md`, and mode `prototype` UI contract artifacts.
- Constrain acceptance to design-stage prototype only for this phase.

### Out
- Production React page implementation.
- Business behavior, backend APIs, and runtime data mutation.

## External Behavior

### console-design-system-prototype-foundation-01
When phase 02 implementation starts, every owned admin/tenant shared UI surface in scope has a declared route, selector contract, and prototype entry with one traceable behavior.

### console-design-system-prototype-foundation-02
Where owned obligations are static/admin/tenant pages, when the design artifact is inspected, each obligation's route, selector, requirement, catalog test, and Playwright identifier are explicitly recorded and consistent.

### console-design-system-prototype-foundation-03
Where phase 02 review/validation runs, if any owned PRD obligation is missing from SPEC, UI matrix, or UI element contract, validation must fail closed.

## Internal Behavior

### console-design-system-prototype-foundation-04
A `prototype`-mode `EVIDENCE/ui-contract.json` must be generated with checksum-bound `manifest`, `pencil`, `prototype`, and `prototype_playwright` sections that are internally consistent with discovered routes/selectors.

### console-design-system-prototype-foundation-05
All plan artifacts, obligations, and TODO evidence references remain planning-only and must not close delivery TODOs.

## Errors and boundaries

| Case | Required outcome | Behavior ID |
| --- | --- | --- |
| missing artifact file | validation `BLOCKER` with exact missing-file diagnostics | console-design-system-prototype-foundation-01 |
| mismatched obligation set across SPEC/UI files | validation `BLOCKER` on exact-obligation trace | console-design-system-prototype-foundation-03 |
| selector format or uniqueness failure | validation `BLOCKER` with UI parser diagnostics | console-design-system-prototype-foundation-02 |

## Verification

### console-design-system-prototype-foundation-06
Where planning validators run on this phase, they must verify all owned obligations and required artifact contracts are present, consistent, and fail closed.

## Requirement trace

| PRD requirement | Behavior IDs | Verification IDs |
| --- | --- | --- |
| PROJECT-UI-CONTRACT | console-design-system-prototype-foundation-01 | 02-UI-SPEC.md |
| PROJECT-ADMIN-IA | console-design-system-prototype-foundation-01 | TEST-MATRIX.md |
| PROJECT-TENANT-IA | console-design-system-prototype-foundation-01 | TEST-MATRIX.md |
| PROJECT-PLANNING-TRACE | console-design-system-prototype-foundation-03 | 02-CONTEXT.md |

## Owned obligations

- OBL-DESIGN-SYSTEM-001
- OBL-DESIGN-SYSTEM-002
- OBL-DESIGN-SYSTEM-003
- OBL-DESIGN-SYSTEM-004
- OBL-DESIGN-SYSTEM-005
- OBL-IA-ADMIN-LOGIN
- OBL-IA-ADMIN-DASH-REALTIME
- OBL-IA-ADMIN-DASH-KPI
- OBL-IA-ADMIN-DASH-ALERT
- OBL-IA-ADMIN-DASH-CONFIG
- OBL-IA-ADMIN-USERS
- OBL-IA-ADMIN-TENANTS
- OBL-IA-ADMIN-TENANT-TRIAL-CONTRACT
- OBL-IA-ADMIN-TENANT-TERMINATION
- OBL-IA-ADMIN-TENANT-ACCESS
- OBL-IA-ADMIN-TENANT-RECHARGE
- OBL-IA-ADMIN-ROLES
- OBL-IA-ADMIN-CHANNEL-CONFIG
- OBL-IA-ADMIN-CHANNEL-MONITOR
- OBL-IA-ADMIN-ROUTING
- OBL-IA-ADMIN-PORTABILITY
- OBL-IA-ADMIN-SEND-MONITOR
- OBL-IA-ADMIN-API-STATUS
- OBL-IA-ADMIN-TASK-MONITOR
- OBL-IA-ADMIN-SIGNATURE-REVIEW
- OBL-IA-ADMIN-TEMPLATE-REVIEW
- OBL-IA-ADMIN-EXEMPTIONS
- OBL-IA-ADMIN-BLACKLIST
- OBL-IA-ADMIN-RISK-PROVIDER
- OBL-IA-ADMIN-FREQUENCY
- OBL-IA-ADMIN-CONTENT
- OBL-IA-ADMIN-NUMBER
- OBL-IA-ADMIN-COMPLAINTS
- OBL-IA-ADMIN-UPLINK
- OBL-IA-ADMIN-UNSUBSCRIBE
- OBL-IA-ADMIN-DETAIL-SUBMIT
- OBL-IA-ADMIN-DETAIL-BULK
- OBL-IA-ADMIN-DETAIL-UPLINK
- OBL-IA-ADMIN-DETAIL-RECEIPT
- OBL-IA-ADMIN-DETAIL-ERROR
- OBL-IA-ADMIN-EXPORT
- OBL-IA-ADMIN-STATS
- OBL-IA-ADMIN-REPORTS
- OBL-IA-ADMIN-RECONCILIATION
- OBL-IA-ADMIN-SETTLEMENT
- OBL-IA-ADMIN-INVOICE
- OBL-IA-ADMIN-PROFIT
- OBL-IA-ADMIN-FEE-WARNING
- OBL-IA-ADMIN-ALERT-RULES
- OBL-IA-ADMIN-ALERT-HISTORY
- OBL-IA-ADMIN-NOTIFICATION-TARGETS
- OBL-IA-ADMIN-SHORTLINKS
- OBL-IA-ADMIN-STATUS-CODES
- OBL-IA-ADMIN-PREFIXES
- OBL-IA-ADMIN-JOBS
- OBL-IA-ADMIN-ACCOUNTS
- OBL-IA-ADMIN-LOGS
- OBL-IA-TENANT-LOGIN
- OBL-IA-TENANT-REGISTER
- OBL-IA-TENANT-ACCOUNT
- OBL-IA-TENANT-QUALIFICATION
- OBL-IA-TENANT-ADMINS
- OBL-IA-TENANT-OVERVIEW
- OBL-IA-TENANT-SEND
- OBL-IA-TENANT-BULK
- OBL-IA-TENANT-SCHEDULE
- OBL-IA-TENANT-SEND-RECORDS
- OBL-IA-TENANT-TEMPLATES
- OBL-IA-TENANT-SIGNATURES
- OBL-IA-TENANT-BALANCE
- OBL-IA-TENANT-RECHARGE
- OBL-IA-TENANT-STATEMENTS
- OBL-IA-TENANT-INVOICES
- OBL-IA-TENANT-BLACKLIST
- OBL-IA-TENANT-WEBHOOK
- OBL-IA-TENANT-API-KEYS
- OBL-IA-TENANT-CMPP
- OBL-IA-TENANT-NOTIFICATION-TARGETS
- OBL-IA-TENANT-UPLINKS
- OBL-IA-TENANT-UNSUBSCRIBES
- OBL-IA-TENANT-SHORTLINKS
- OBL-EDGE-EMPTY-LIST
- OBL-EDGE-REVIEW-VALIDATION
