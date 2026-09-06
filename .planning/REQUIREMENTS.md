# Requirements

## Contract

This catalog preserves all 108 top-level requirement groups synthesized from `docs/PRD.md`: 99 functional groups and 9 cross-module non-functional groups. Detailed fields, state transitions, exceptions, information architecture, data rules, cross-module flows, and acceptance clauses are enumerated as 522 stable atomic obligations in `.planning/PRD-OBLIGATIONS.md`.

Every atomic record has a machine-readable `Requirement IDs` field. Values are comma-separated IDs from this file (`REQ-F-*` or `REQ-NFR-*`) or from the explicitly registered project-owned `PROJECT-*` namespace in `PRD-OBLIGATIONS.md`. Project IDs do not increase the 108 top-level requirement count. Each top-level group has one primary integration phase, every one of the 108 IDs is linked by at least one atomic record, and unknown requirement IDs fail validation.

Atomic sub-obligations may belong to focused peer phases, and the top-level group cannot close until every linked atomic obligation is verified. Completion requires bidirectional requirement/obligation/behavior/test/evidence trace, a final blocking-free review result, and an empty scoped `TODO.md`.

## Atomic trace and closure

- Validate the complete 108↔522 mapping with `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --assert-unique --assert-traced`.
- Query a group with `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --requirement <REQ-ID> --assert-unique --assert-traced`; the command returns only records whose `Requirement IDs` field in the nine-field schema contains that exact ID, never a source-text guess.
- Query a phase package with `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner <package-id> --assert-unique --assert-traced`.
- The query resolves every numbered sub-behavior and unnumbered field, state, UI, exception, flow, data, permission, display, or DoD obligation linked to that group.
- The primary phase performs final group integration; it cannot mark the group complete while a peer-owned atomic obligation lacks executed evidence.
- Project closure runs the validator's complete requirement coverage check and the atomic-obligation/TODO evidence query; unknown, uncovered, duplicate, or nonempty TODO/error results fail closed.

## Functional requirements

### Account and access

| Requirement | Required outcome | Owning phase |
| --- | --- | --- |
| REQ-F-1-1 | Administrators manage platform accounts, roles, validity, disablement, and change history. | Phase 5 |
| REQ-F-1-2 | Administrators manage custom menu, button, API, and data permissions with safe role migration. | Phase 5 |
| REQ-F-1-3 | Tenant administrators manage tenant-scoped subaccounts and roles without cross-tenant access. | Phase 9 |
| REQ-F-1-4 | Users receive secure login, lockout, session expiry, login history, and unusual-login notification. | Phase 5 |
| REQ-F-1-5 | Each role sees an authorized account overview, login information, balance, usage, and service state. | Phase 44 |

### Tenant lifecycle

| Requirement | Required outcome | Owning phase |
| --- | --- | --- |
| REQ-F-2-1 | Tenants submit all qualification fields and evidence and can observe certification state. | Phase 8 |
| REQ-F-2-2 | Operators approve, reject, or request supplements and prevent unqualified tenants from sending or applying for resources. | Phase 8 |
| REQ-F-2-3 | Authorized users maintain tenant, contact, commercial, and recertification information. | Phase 8 |
| REQ-F-2-4 | Operators enable, disable, or freeze a tenant while preserving historical read access. | Phase 8 |
| REQ-F-2-5 | Finance or operations configures prepaid/postpaid mode, credit, and billing period rules. | Phase 37 |
| REQ-F-2-6 | Tenant developers create/revoke API keys and request/manage downstream CMPP credentials. | Phase 9 |
| REQ-F-2-7 | Tenant administrators manage tenant subaccounts and permissions from account settings. | Phase 9 |
| REQ-F-2-8 | Approved tenants enter trial, consume adjustable trial quota, and freeze correctly at quota or validity boundaries. | Phase 22 |
| REQ-F-2-9 | Trial tenants become contracted under a versioned price, billing mode, credit, billing period, and contract record. | Phase 37 |
| REQ-F-2-10 | Approved termination blocks all ingress, revokes credentials/resources, settles finances, and retains history. | Phase 49 |

### Signatures and templates

| Requirement | Required outcome | Owning phase |
| --- | --- | --- |
| REQ-F-3-1 | Tenants submit signature text, type, usage, and proof into a traceable review state. | Phase 12 |
| REQ-F-3-2 | Operators search, risk-assess, review, and return decisions on signature applications. | Phase 12 |
| REQ-F-3-3 | Approved signatures are filed per upstream channel and usable only on channels with successful filing. | Phase 12 |
| REQ-F-3-4 | Tenants submit typed templates with variables, a signature, parameter rules, and rationale. | Phase 13 |
| REQ-F-3-5 | Operators review template content and variable safety and return traceable decisions. | Phase 13 |
| REQ-F-3-6 | Authorized users configure scoped, expiring exemptions while all exempt activity remains auditable. | Phase 14 |
| REQ-F-3-7 | Every HTTP/CMPP send uses an approved compatible signature/template and valid variables. | Phase 13 |
| REQ-F-3-8 | Users inspect signature/template usage, success, and rejection metrics. | Phase 34 |

### Upstream channels

| Requirement | Required outcome | Owning phase |
| --- | --- | --- |
| REQ-F-4-1 | Operators configure complete protocol, carrier, credential, connection, price, priority, and availability data. | Phase 10 |
| REQ-F-4-2 | Validated channel changes take effect without service restart. | Phase 10 |
| REQ-F-4-3 | Health checks expose connectivity and quality and place unhealthy channels into maintenance with an alert. | Phase 11 |
| REQ-F-4-4 | Channel retirement detects dependencies and migrates them before the channel is taken offline. | Phase 10 |
| REQ-F-4-5 | Operators compare channel volume, success, latency, cost, and profit. | Phase 34 |
| REQ-F-4-6 | Operators create channel groups with weight or primary/backup semantics. | Phase 11 |
| REQ-F-4-7 | Manual or automatic pause removes a channel from routing, moves work safely, and records recovery evidence. | Phase 11 |

### Routing and risk controls

| Requirement | Required outcome | Owning phase |
| --- | --- | --- |
| REQ-F-5-1 | System, tenant, and third-party blacklist decisions happen before task creation and remain traceable. | Phase 16 |
| REQ-F-5-2 | Authorized users manage/import/export blacklists and whitelists with whitelist precedence. | Phase 16 |
| REQ-F-5-3 | Operators configure third-party risk providers, thresholds, credentials, timeout behavior, and cached fallback. | Phase 16 |
| REQ-F-5-4 | Operators analyze intercept source and suspected false positives and manage appeals. | Phase 16 |
| REQ-F-5-5 | Runtime final-content scanning applies scoped hot-updated block, replace, or alert actions with metrics. | Phase 17 |
| REQ-F-5-6 | Configurable number, tenant, IP, and content-similarity limits block, delay, or alert with exemptions. | Phase 18 |
| REQ-F-5-7 | Routing identifies carrier/location with portability lookup, caching, and prefix fallback. | Phase 19 |
| REQ-F-5-8 | Ordered multi-condition rules select a channel/group/weight target or the explicit default. | Phase 21 |
| REQ-F-5-9 | Weighted and primary/backup routing uses real health data for circuit open, fallback, and recovery. | Phase 21 |
| REQ-F-5-10 | Error-class retry rules cap attempts, preserve history, and prevent duplicate contact. | Phase 21 |

### Downstream access and sending

| Requirement | Required outcome | Owning phase |
| --- | --- | --- |
| REQ-F-6-1 | The REST single-send API returns a truthful acceptance result and stable message ID. | Phase 23 |
| REQ-F-6-2 | The REST bulk API accepts per-recipient variables and returns batch and item tracking IDs. | Phase 29 |
| REQ-F-6-3 | API consumers query message state by ID or authorized time range. | Phase 24 |
| REQ-F-6-4 | HMAC, timestamp, Redis nonce, and IP allow-list checks authenticate every HTTP API request. | Phase 23 |
| REQ-F-6-5 | Per-key second/minute/hour/day limits return standard 429 behavior. | Phase 18 |
| REQ-F-6-6 | Status and uplink Webhooks retry with backoff and support authorized manual replay. | Phase 28 |
| REQ-F-6-7 | The downstream CMPP gateway handles authenticated standard connection and message flows. | Phase 31 |
| REQ-F-6-8 | Service_Id/TLV product-template bindings enforce the same compliance policy and return precise errors. | Phase 31 |
| REQ-F-6-9 | Requested status reports are pushed through CMPP_DELIVER. | Phase 31 |
| REQ-F-6-10 | Tenant users send and preview single or small-batch messages from the authenticated console. | Phase 26 |
| REQ-F-6-11 | Tenant users import recipient files with individualized variables into a validated batch. | Phase 29 |
| REQ-F-6-12 | Users schedule, prioritize, pause, resume, cancel, and restart supported jobs. | Phase 29 |

### Message details and evidence

| Requirement | Required outcome | Owning phase |
| --- | --- | --- |
| REQ-F-7-1 | Authorized users inspect every ingress submission, source, tenant, state, and rejection reason. | Phase 24 |
| REQ-F-7-2 | Authorized users filter, inspect, export, resend, or appeal send records with masked sensitive data. | Phase 27 |
| REQ-F-7-3 | Users inspect batch totals, execution state, cost, result rate, and recipient-level details. | Phase 29 |
| REQ-F-7-4 | Users inspect, export, correct, or replay receipts with channel and error information. | Phase 27 |
| REQ-F-7-5 | Users search uplink records, inspect push state, configure supported response behavior, and replay failures. | Phase 32 |
| REQ-F-7-6 | Users analyze failure distributions and safely bulk-retry or mark issues. | Phase 27 |
| REQ-F-7-7 | Users manage failed status/uplink pushes, retry policy, alerts, pause, and tenant notification. | Phase 28 |
| REQ-F-7-8 | A secure asynchronous export center creates, tracks, retries, and downloads supported formats. | Phase 46 |
| REQ-F-7-9 | Users search/export unsubscribe evidence with tenant, signature/product, and handling outcome. | Phase 33 |

### Billing and finance

| Requirement | Required outcome | Owning phase |
| --- | --- | --- |
| REQ-F-8-1 | Prepaid acceptance reserves funds, delivery confirms charge, non-charge failure reverses, and insufficient funds rejects. | Phase 22 |
| REQ-F-8-2 | Postpaid usage enforces configurable credit policy and creates period statements. | Phase 37 |
| REQ-F-8-3 | Tenants request recharge and finance records an auditable approval and transaction result. | Phase 36 |
| REQ-F-8-4 | Every charge produces a task-linked consumption ledger queryable by tenant, period, and business type. | Phase 22 |
| REQ-F-8-5 | Finance and tenants reconcile generated statements and resolve recorded differences. | Phase 38 |
| REQ-F-8-6 | Finance creates and advances settlement records through supported states. | Phase 38 |
| REQ-F-8-7 | Tenants request invoices and finance records issuance and status. | Phase 38 |
| REQ-F-8-8 | Authorized users analyze cost, revenue, and profit by tenant, channel, and period. | Phase 39 |
| REQ-F-8-9 | Every balance mutation is immutable, attributable, searchable, and exportable. | Phase 22 |
| REQ-F-8-10 | Configurable prepaid/postpaid thresholds notify the right parties and enforce the selected credit action. | Phase 40 |

### Complaints and uplink compliance

| Requirement | Required outcome | Owning phase |
| --- | --- | --- |
| REQ-F-9-1 | Operators register complaints and link all available tenant, channel, signature, template, and message evidence. | Phase 41 |
| REQ-F-9-2 | Operators progress complaint cases through a traceable state machine with remediation records. | Phase 41 |
| REQ-F-9-3 | Complaint handling can blacklist a number or pause the linked tenant, signature, template, or channel. | Phase 41 |
| REQ-F-9-4 | Operators analyze complaint trends and high-risk tenants, signatures, and content types. | Phase 41 |
| REQ-F-9-5 | Sustained complaint/failure/unsubscribe risk alerts and optionally pauses a tenant with reviewed recovery. | Phase 42 |
| REQ-F-10-1 | Operators search all authorized uplink traffic by tenant, number, content, carrier, time, and push state. | Phase 32 |
| REQ-F-10-2 | Configurable unsubscribe recognition blacklists the tenant recipient, records evidence, and notifies the tenant. | Phase 33 |
| REQ-F-10-3 | Users inspect unsubscribe volume/rate by tenant and signature and can trigger alert policy. | Phase 33 |
| REQ-F-10-4 | Operators monitor tenant uplink-push reliability and handle persistently bad destinations. | Phase 32 |

### Analytics and dashboards

| Requirement | Required outcome | Owning phase |
| --- | --- | --- |
| REQ-F-11-1 | Users compare channel volume, success, cost, and latency from real aggregates. | Phase 34 |
| REQ-F-11-2 | Authorized users analyze tenant sending, spend, and activity. | Phase 34 |
| REQ-F-11-3 | Authorized users analyze signature/template use, success, and rejection. | Phase 34 |
| REQ-F-11-4 | Users build visual reports from supported dimensions and periods. | Phase 43 |
| REQ-F-11-5 | The platform dashboard shows global KPIs, send trend, activity, and manual refresh. | Phase 44 |
| REQ-F-11-6 | The operations dashboard shows real send, tenant, success, revenue, rank, and channel health metrics. | Phase 44 |
| REQ-F-11-7 | The alert dashboard shows counts, tabs, details, acknowledge, resolve, and mute behavior. | Phase 35 |
| REQ-F-11-9 | Channel/tenant complaint ratios use real monthly data, threshold ordering, drill-down, pause, and historical period selection. | Phase 45 |
| REQ-F-11-10 | Administrators configure dashboard visibility, metric definitions, refresh policy, threshold, and role-specific views. | Phase 44 |

### Alerts, tools, and audit

| Requirement | Required outcome | Owning phase |
| --- | --- | --- |
| REQ-F-12-1 | Operators configure typed threshold, duration, severity, and activation rules over real metrics. | Phase 35 |
| REQ-F-12-2 | Alert notifications reach configured recipients through supported channels with delivery evidence. | Phase 35 |
| REQ-F-12-3 | Operators search, acknowledge, resolve, mute, and audit alert history. | Phase 35 |
| REQ-F-13-1 | Tenants/operators create expiring approved short links and inspect click analytics. | Phase 48 |
| REQ-F-13-2 | Automated and human review prevents unsafe pre-approval redirects and continuously handles target drift. | Phase 48 |
| REQ-F-13-3 | Operators maintain carrier/provider-to-platform status mappings and advice. | Phase 20 |
| REQ-F-13-4 | Operators import and incrementally maintain carrier prefix attribution data. | Phase 19 |
| REQ-F-13-5 | Operators manage all supported send jobs from one authorized control surface. | Phase 29 |
| REQ-F-14-1 | All backend operations emit searchable user, request, result, IP, and latency audit records. | Phase 6 |
| REQ-F-14-2 | Unusual login, export, and repeated-failure activity is detected and alerted. | Phase 6 |

## Cross-module non-functional requirements

| Requirement | Required outcome | Owning phase |
| --- | --- | --- |
| REQ-NFR-PERFORMANCE | Executed load evidence proves required throughput, latency, capacity, and health-check behavior. | Phase 52 |
| REQ-NFR-SECURITY | TLS, JWT/RBAC, HMAC, replay protection, allow-lists, limits, CMPP isolation, and adversarial authorization are proven. | Phase 51 |
| REQ-NFR-DATA-PROTECTION | Sensitive identity, contact, credential, and financial data is encrypted, masked, key-managed, and access-audited. | Phase 3 |
| REQ-NFR-RELIABILITY | Stateless operation, dependency resilience, multi-instance behavior, failover, rollback, and recovery are proven. | Phase 53 |
| REQ-NFR-EXTENSIBILITY | Connector, routing, billing, and review extension contracts are plugin/configuration based and proven with a conformance implementation. | Phase 55 |
| REQ-NFR-COMPATIBILITY | Phase 1 provides fail-closed runtime verification for the current Google Chrome installed at the standard local path, one 1440x900 desktop layout, simplified-Chinese copy/export, and UTC+8/IANA-timezone contracts; Phase 56 reruns those contracts against all delivered production surfaces and completes product acceptance. No browser is downloaded, no ChromeDriver is used, and Edge, Safari, Firefox, IE, Chromium, and other browsers are unsupported and outside acceptance. | Phase 1 (verification foundation); Phase 56 (product acceptance) |
| REQ-NFR-OBSERVABILITY | Required business events, traces, metrics, logs, alerts, and source-consistent aggregates are implemented and proven. | Phase 54 |
| REQ-NFR-RETENTION | Partitioned hot data and encrypted cold archives retain, restore, search, and export regulated evidence. | Phase 47 |
| REQ-NFR-ERROR-IDEMPOTENCY | All specified failures have consistent behavior and repeated submissions cannot duplicate send or charge. | Phase 23 |

## Coverage invariant

- Functional requirements mapped: 99.
- Non-functional requirements mapped: 9.
- Total requirements mapped: 108.
- Atomic obligations cataloged: 522.
- Top-level IDs with at least one atomic link: 108 of 108.
- Unknown `REQ-*`/`PROJECT-*` links: none.
- Duplicate `Requirement IDs` within one atomic record: none.
- Catalog owners represented by roadmap packages: 56 of 56.
- Orphaned requirements: none.
- Requirements with more than one owning phase: none.
