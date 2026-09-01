# YCSOpen SMS Roadmap

## Governing invariants

This roadmap is dependency-ordered and evidence-driven. It contains no delivery estimates. The only completion signal is a verified empty TODO set for the declared scope, including every owned atomic PRD obligation.

Each phase owns one focused module as a vertical slice. The 108 top-level requirement groups are integration groupings; `.planning/PRD-OBLIGATIONS.md` is the 522-item atomic completion catalog.

## Mandatory artifact and entry contract

Every phase uses `.planning/phases/<NN>-<package-id>/` and contains `<NN>-SPEC.md`, `<NN>-CONTEXT.md`, at least one `<NN>-*-PLAN.md`, plus `INTENT.md`, `DESIGN.md`, `ITERATIONS.md`, `DECISIONS.md`, `TODO.md`, `TEST-MATRIX.md`, `ENTRY-REVIEW.md`, `CLAUDE-REVIEW.md`, and `EVIDENCE/`. UI phases also contain `<NN>-UI-SPEC.md`, `UI-ELEMENTS.md`, Pencil `.pen` source, HTML prototype output, and checksum-bound `EVIDENCE/ui-contract.json`. A phase declaring persistence migrations also contains `SCHEMA-CLAIMS.md` governed by `.planning/SCHEMA-OWNERSHIP.md`.

Before implementation, each phase runs the repository-present standard-library Ruby entry command stated in its detail. The command checks dependency empty-TODO and live annotated delivery attestation, artifact structure, dependency closure, exact owned-obligation trace, nonempty files/action/verify/done plan-task fields, schema ownership/migration conflicts, structured entry review, and authoritative TODO state. It never treats a local commit, tracking ref, status, or `SUMMARY.md` SHA label as remote evidence. For every UI phase, `--ui` invokes only `validate-ui-contract.rb --stage design`: UI-SPEC, strict inventories/traces, Pencil, HTML, manifest, and prototype interaction tests. It deliberately does not require React that the phase has not implemented. Validator self-tests do not authorize a real phase; absent phase artifacts fail closed.

An independent verification subagent writes `ENTRY-REVIEW.md`. Every criterion has one `PASS` or `BLOCKER`, an evidence path, and a reproducible command or inspection rule. Missing artifacts, uncovered obligations, non-runnable checks, contradictions, or any `BLOCKER` fail closed. Review findings use a bounded cycle of at most three attempts. A non-decreasing finding count or an exhausted cycle escalates with TODOs still open; escalation never authorizes implementation, and a new cycle requires new developer decisions or evidence.

The executable sequence is preflight, independent entry review, correction, then the same entry command again with the completed `ENTRY-REVIEW.md`. Only the final zero-exit result authorizes implementation; an expected preflight failure never counts as entry approval.

For UI, Pencil `.pen` is the visual source, HTML prototype is the clickable interaction source, and React is the production source. Stable page/state IDs map all three. Production deviations update `UI-ELEMENTS.md`, `DECISIONS.md`, and `TEST-MATRIX.md`, then rerun UI reconciliation.

Playwright acceptance is derived jointly from the owned PRD obligations and the actual implemented route/API/persistence/event behavior. Every browser action and assertion traces through `UI-ELEMENTS.md` and `TEST-MATRIX.md` to a documented stable `data-testid`; lower-layer tests cover truths the browser cannot establish honestly.

## Fixed exit and delivery contract

Every entry reviewer, plan checker, GSD goal verifier/code reviewer, and Claude reviewer uses the same bounded revision cycle: no more than three review attempts per cycle; a non-decreasing BLOCKER/HIGH count or the third unresolved attempt escalates with TODOs still open. Escalation never authorizes implementation or completion. A new cycle starts only after new developer decisions or evidence. Before TODO-empty/commit, every production UI phase runs its exact `validate-ui-contract.rb --stage production` command from its UI contract and requires PASS over design, React, per-obligation browser blocks, and checksum-bound executed evidence; Phase 2 reruns design because it is prototype-only. The final accepted result must contain no blocking/HIGH finding, and the phase obligation and scoped TODO queries must be empty except for the reserved external-delivery item. One atomic phase commit is pushed to the configured GitHub remote; one deterministic annotated tag then carries the commit/tree/tested-subject/subject-manifest/evidence-manifest/PR-check/PASS attestation. `SUMMARY.md` contains the remote, full branch ref, deterministic tag locator, PR locator, and check name rather than an impossible self SHA. The live validator recomputes the target-tree subject and resolves the PR/check before the effective TODO query becomes empty and `STATE.md` advances. This paragraph is authoritative wherever a phase detail uses the older shorthand “remote SHA.”

## Phases

- [ ] **Phase 1: Engineering verification and drift-control foundation** — Truthful backend/frontend/integration/browser commands.
- [ ] **Phase 2: Console design system and prototype foundation** — Complete Admin/Tenant page registry and role matrix.
- [ ] **Phase 3: Cryptographic storage and migration bootstrap** — Envelope encryption and KMS/HSM adapter.
- [ ] **Phase 4: Platform system-message and notification bootstrap** — Controlled platform templates.
- [ ] **Phase 5: Console identity and platform RBAC** — Password hashing inside identity.
- [ ] **Phase 6: Privileged data access, operation audit, and security detection** — Masked default views.
- [ ] **Phase 7: Platform system configuration** — Typed, versioned, auditable production settings.
- [ ] **Phase 8: Tenant qualification and status** — All qualification fields/files.
- [ ] **Phase 9: Tenant subaccounts and access credentials** — Tenant roles/subaccounts and isolation.
- [ ] **Phase 10: Channel configuration lifecycle** — Protocol/carrier/connectivity/credential/connection/price/priority/availability schema.
- [ ] **Phase 11: Channel health, pools, and candidate pause** — Heartbeat/test health.
- [ ] **Phase 12: Signature lifecycle and channel filing** — Signature application/proof/risk.
- [ ] **Phase 13: Template lifecycle and send compliance contract** — Template fields/variables/rules/signature binding.
- [ ] **Phase 14: Auditable exemption policy** — Signature/content/account exemption types.
- [ ] **Phase 15: Unified resource review history** — Cross-resource immutable review decisions.
- [ ] **Phase 16: Blacklist and third-party risk control** — System/tenant black/white lists.
- [ ] **Phase 17: Runtime final-content safety** — Sensitive word/category/level/replacement/action/scope/state.
- [ ] **Phase 18: Frequency and API rate controls** — Per-key second/minute/hour/day limits.
- [ ] **Phase 19: Number attribution and portability** — Prefix import/incremental versions.
- [ ] **Phase 20: Provider status taxonomy and normalization** — Provider/protocol code taxonomy.
- [ ] **Phase 21: Routing, circuit, and retry policy** — Ordered multi-condition routing/default.
- [ ] **Phase 22: Trial and prepaid ledger** — Trial activation/config/consume/freeze/convert request.
- [ ] **Phase 23: Secure HTTP message acceptance** — Canonical body HMAC.
- [ ] **Phase 24: HTTP upstream delivery, receipt, and final status** — HTTP connector SPI implementation.
- [ ] **Phase 25: Durable dispatch task migration and recovery** — Task ownership/lease.
- [ ] **Phase 26: Authenticated tenant console send** — JWT console adapter.
- [ ] **Phase 27: Message, receipt, and error operations** — Full filters/columns.
- [ ] **Phase 28: Generic Webhook delivery transport** — Tenant callback configuration.
- [ ] **Phase 29: Bulk, scheduled, and task operations** — Bulk API.
- [ ] **Phase 30: Upstream CMPP connector** — CMPP client codec.
- [ ] **Phase 31: Downstream CMPP gateway** — CMPP server.
- [ ] **Phase 32: Uplink normalization, search, and push** — HTTP/CMPP uplink normalization.
- [ ] **Phase 33: Unsubscribe suppression, evidence, and statistics** — Global/tenant keyword library.
- [ ] **Phase 34: Core statistics aggregation pipeline** — Canonical metric/source/formula/freshness/permission registry.
- [ ] **Phase 35: Alert engine, notification routing, and console** — Typed source-backed rules.
- [ ] **Phase 36: Tenant recharge operations** — Recharge request fields/proof.
- [ ] **Phase 37: Contract pricing and postpaid credit** — Trial conversion.
- [ ] **Phase 38: Reconciliation, settlement, and invoices** — Source-backed statements.
- [ ] **Phase 39: Financial source analytics** — Actual upstream cost.
- [ ] **Phase 40: Fee warning and credit enforcement** — Prepaid amount/estimated-use warnings.
- [ ] **Phase 41: Complaint case management** — Complaint source/intake.
- [ ] **Phase 42: Tenant risk warning and auto-pause** — Complaint/failure/unsubscribe rates from source registry.
- [ ] **Phase 43: Custom report authoring** — Supported dimension/measure registry.
- [ ] **Phase 44: Operational dashboards and account overview** — Platform realtime KPI/trend/activity.
- [ ] **Phase 45: Complaint-ratio dashboard and intervention** — Real send denominator/complaint numerator.
- [ ] **Phase 46: Secure asynchronous export** — Export types/formats.
- [ ] **Phase 47: Retention, archive, and restore** — Hot partition policy.
- [ ] **Phase 48: Short-link creation and safety review** — URL/domain/validity.
- [ ] **Phase 49: Tenant cooperation termination** — Machine-readable participant inventory.
- [ ] **Phase 50: Tenant help and developer center** — Versioned guide, API docs, and customer-service entry.
- [ ] **Phase 51: Security assurance** — Full TLS/mTLS boundary.
- [ ] **Phase 52: Performance and capacity assurance** — Realistic traffic/data model.
- [ ] **Phase 53: Reliability and HA assurance** — Stateless multi-instance topology.
- [ ] **Phase 54: Observability assurance** — All PRD business events.
- [ ] **Phase 55: Extension conformance assurance** — Connector, routing condition/action, billing/price, review policy, notification adapter extension contracts.
- [ ] **Phase 56: Final cross-protocol release acceptance** — Composition only: verify all 108 groups/all 522 atomic obligations.

## Phase Details

### Phase 1: Engineering verification and drift-control foundation

**Package ID**: `engineering-verification-foundation`
**Goal**: All repository verification layers return deterministic pass/fail output and preserve diagnostic evidence.
**In scope**: Truthful backend/frontend/integration/browser commands; the current Google Chrome installed at `/Applications/Google Chrome.app/Contents/MacOS/Google Chrome` executing one 1440x900 smoke-and-visual scenario through Playwright without downloads or ChromeDriver; phase-entry/TODO/trace validators; machine-readable obligation queries; UI manifest schema; route/page/DOM/test-ID/Playwright bidirectional drift checks; dynamic-row selector law; evidence metadata; reusable fail-closed simplified-Chinese copy/export contract validation; reusable UTC+8/IANA-timezone verification over synthetic and real-service fixtures.
**Out of scope**: Chrome version matrices, downloaded browser archives, ChromeDriver, Edge, Safari, Firefox, IE, Chromium, and other-browser compatibility; business behavior, visual design decisions, final first-release Chinese coverage, and international-message product persistence; Phase 56 owns the latter two product acceptance obligations.
**Depends on**: Nothing.
**Requirements**: REQ-NFR-COMPATIBILITY verification foundation; Phase 56 completes the corresponding product acceptance.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner engineering-verification-foundation --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Maven/npm/Playwright scripts, planning validators, UI manifest schema, AST/ESLint rules, trace reports.
**Entry gate**: `01-00-PLAN.md` is the sole plan executable while Phase 1 ENTRY is BLOCKED. It repairs the repository-owned entry probe at the standard path `/Applications/Google Chrome.app/Contents/MacOS/Google Chrome`, executes only fixed-argv `--version` plus an isolated-profile headless synthetic local page, and writes entry-only `EVIDENCE/local-chrome-entry.json`; it has no Playwright dependency and does not create Plan 06 runtime/smoke evidence. An independent reviewer who did not execute `01-00` must rerun the probe, inspect exactly 13 Phase 1 plans and seven owned-obligation traces, and replace the current placeholder with unique rows using exactly `Criterion ID | Verdict | Evidence | Command or inspection rule` plus final `## Verdict`. The revised bootstrap must dynamically discover or explicitly require all 13 plans and must reject missing artifacts, absent owned-ID traces, malformed/duplicate/empty criterion rows, executor self-approval, missing final PASS, or any BLOCKER. It must not consume the superseded Chrome for Testing admission/probe/attestation digest chain. Only independent ENTRY PASS followed by a zero-exit real bootstrap authorizes `01-05` and the remaining plans. The bounded revision rules in `EXECUTION-STANDARD.md` remain applicable.
**Success Criteria** (observable truths):

1. All repository verification layers return deterministic pass/fail output and preserve diagnostic evidence.
2. A validator detects missing or extra routes, manifest pages, DOM test IDs, dynamic-row contracts, and Playwright selectors in both directions.
3. Atomic obligation and TODO queries fail when ownership, trace, evidence, or checked-item rules are violated.
4. Later phases can rerun versioned simplified-Chinese copy/export and UTC+8/IANA-timezone contracts, while Phase 1 makes no claim that future product surfaces or international-message persistence already pass.

**Test layers**: Validator self-tests, fixture repos, build/lint/type checks, MySQL/Redis/browser smoke.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and every non-delivery owned-obligation/TODO query is empty. Then create and push one atomic commit, create the deterministic annotated delivery tag, and run the live validator over configured remote/branch/tag/commit/tree, target-tree subject/evidence/review digests, and PR/check identity. `SUMMARY.md` records locators, not its own final SHA; only live attestation PASS closes the reserved delivery item.
**Plans**: 13 focused plan files in dependency waves 0 through 8:

- [x] `01-00-PLAN.md` — Sole pre-entry remediation: local-Chrome entry probe, independent review, and revised bootstrap.
- [x] `01-01-PLAN.md` — Evidence kernel and root orchestrator.
- [x] `01-02-PLAN.md` — Exact trace closure and lifecycle gates.
- [x] `01-03-PLAN.md` — Annotated remote delivery attestation contract.
- [x] `01-04-PLAN.md` — UI relation drift and versioned row-key registry.
- [x] `01-05-PLAN.md` — Existing `/login` selectors and shared structural scenario.
- [x] `01-06-PLAN.md` — Current local Google Chrome 1440x900 smoke and visual evidence.
- [x] `01-07-PLAN.md` — Reusable simplified-Chinese copy/export contract validator.
- [x] `01-08-PLAN.md` — Real MySQL/Redis and UTC+8/IANA verifier contract.
- [x] `01-09-PLAN.md` — Root-lane and CI integration.
- [ ] `01-10-PLAN.md` — Seven-row acceptance evidence production; attempt-1 seal invalidated by independent review and reopened for correction/reseal.
- [ ] `01-11-PLAN.md` — GSD and Claude reviews.
- [ ] `01-12-PLAN.md` — TODO closure, atomic commit, PR, and annotated delivery attestation.

### Phase 2: Console design system and prototype foundation

**Package ID**: `console-design-system-prototype-foundation`
**Goal**: Every PRD Admin/Tenant page and global destination has a stable page/route/role/state entry.
**In scope**: Complete Admin/Tenant page registry and role matrix; pinned ycsan brand revision/token/screenshot snapshot; design tokens; prototype shells; navigation/header/breadcrumb/notification destination/profile; shared dense component specifications and all global states; local Pencil MCP plus `/Users/laosanzheong/Documents/codebases/hengshi-jarvis/projectlogs/907/uiskill`; Pencil visual baseline; clickable HTML prototype baseline; target UI manifest and test-ID registry.
**Out of scope**: Business APIs, React production shell code, and every production business page.
**Depends on**: Phase 1.
**Requirements**: Atomic IA, UI, permission, and display obligations in PRD-OBLIGATIONS.md.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner console-design-system-prototype-foundation --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Pencil `.pen`, `design-output/` HTML, page/state registry, Admin/Tenant shells, shared component catalog.
**UI contract**: Establish the project token, shell, and component design contracts and map stable page/state IDs across Pencil and HTML while registering the target React route/test-ID contract without implementing React production UI; inventory every global page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 02 --package console-design-system-prototype-foundation --stage design`. This is the design entry gate and Phase 2 reruns the same design gate before TODO-empty/commit; production stage is not applicable to this prototype-only module. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/02-console-design-system-prototype-foundation`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 02 --package console-design-system-prototype-foundation --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/02-console-design-system-prototype-foundation/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; this prototype-only phase reruns design at exit and cannot claim React production evidence. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Every PRD Admin/Tenant page and global destination has a stable page/route/role/state entry.
2. Pencil visual source and clickable HTML interaction source map by stable page/state IDs and pass design/PRD coverage review.
3. Token, shell, component, state, and test-ID design contracts are reusable by every later production UI phase without Phase 2 claiming their React implementation.

**Test layers**: Manifest/schema, prototype-link, accessibility, token/contrast, screenshot and design-consistency checks.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: Four plan files in three dependency waves, all inside the single `console-design-system-prototype-foundation` module:

- Wave 1 — `02-01-PLAN.md`: design tokens, shared component specifications, Admin/Tenant prototype shells, brand provenance, and Pencil/HTML source mapping; no React production implementation. Lane check: `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner console-design-system-prototype-foundation --id-prefix OBL-DESIGN-SYSTEM --assert-unique --assert-traced`.
- Wave 2 — `02-02-PLAN.md`: complete Admin IA and clickable Admin prototype, after 02-01. Lane check: `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner console-design-system-prototype-foundation --id-prefix OBL-IA-ADMIN --assert-unique --assert-traced`.
- Wave 2 — `02-03-PLAN.md`: complete Tenant IA and clickable Tenant prototype, after 02-01. Lane check: `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner console-design-system-prototype-foundation --id-prefix OBL-IA-TENANT --assert-unique --assert-traced`.
- Wave 3 — `02-04-PLAN.md`: cross-portal integration, state/accessibility/design consistency, manifest/test-ID reconciliation, after 02-02 and 02-03. Lane checks: `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner console-design-system-prototype-foundation --id-prefix OBL-EDGE --assert-unique --assert-traced` and `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner console-design-system-prototype-foundation --assert-unique --assert-traced`.

Each lane records its exact obligation subset in its plan frontmatter, SPEC trace, TODO, TEST-MATRIX, and evidence index; a lane cannot borrow another lane's evidence to close.
**UI hint**: yes

### Phase 3: Cryptographic storage and migration bootstrap

**Package ID**: `crypto-storage-bootstrap`
**Goal**: Protected database/object fields and logs contain no prohibited plaintext in executable samples.
**In scope**: Envelope encryption and KMS/HSM adapter; key rotation; protected-field persistence boundary; protected object storage; log redaction; plaintext inventory and migration/rollback.
**Out of scope**: Password hashing and identity credential schema, identity workflows, role decisions, privileged reveal UI, and archive lifecycle.
**Depends on**: Phase 1.
**Requirements**: REQ-NFR-DATA-PROTECTION (storage/key atomic obligations).
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner crypto-storage-bootstrap --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Crypto/key SPI, persistence converters, protected object adapter, migration and leak-scan tools.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/03-crypto-storage-bootstrap`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 03 --package crypto-storage-bootstrap --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/03-crypto-storage-bootstrap/ENTRY-REVIEW.md`. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Protected database/object fields and logs contain no prohibited plaintext in executable samples.
2. Key rotation and rollback preserve data and never persist master keys.
3. Existing plaintext migrates through a verified, resumable, auditable path.

**Test layers**: Crypto vectors, persistence integration, rotation/failure recovery, leak scans.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: 30 executable plan files are registered; `03-01` through `03-04` have committed summaries and `03-05` is the next Wave 2 plan. Completion remains governed only by the scoped TODO query.

### Phase 4: Platform system-message and notification bootstrap

**Package ID**: `platform-system-message-bootstrap`
**Goal**: Registration/operational notifications reach an authoritative provider sandbox through a direct bootstrap adapter without tenant-owned resources or the tenant acceptance pipeline.
**In scope**: Direct platform-bootstrap HTTP provider adapter and SPI; environment/KMS-protected platform provider credential; controlled platform templates; provider acceptance/failure normalization; registration and operational notification send; recipient policy; delivery evidence; recursion guard; retry classification; secret protection; audit event; later adapter replacement contract.
**Out of scope**: Tenant-owned resources, channel database/configuration, routing, normal tenant acceptance/billing, and generic business alert rules.
**Depends on**: Phases 1, and 3.
**Requirements**: Atomic bootstrap/notification obligations in PRD-OBLIGATIONS.md.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner platform-system-message-bootstrap --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Platform notification API/SPI, direct HTTP bootstrap-provider adapter, controlled template registry, environment/KMS secret binding, authoritative provider sandbox contract, delivery evidence.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/04-platform-system-message-bootstrap`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 04 --package platform-system-message-bootstrap --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/04-platform-system-message-bootstrap/ENTRY-REVIEW.md`. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. A real verification message reaches an authoritative provider sandbox through the direct bootstrap adapter before channel/routing modules exist, and its accepted or failed result is durably correlated.
2. A recursion guard prevents notification failure from recursively producing the same notification.
3. Each attempt/result is protected, attributable, and reusable by onboarding and later alert delivery; a later normal-channel adapter may replace bootstrap delivery only through the same SPI and regression suite.

**Test layers**: Template policy unit, provider contract, retry/recursion fault tests, real sandbox evidence.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD

### Phase 5: Console identity and platform RBAC

**Package ID**: `console-identity-platform-rbac`
**Goal**: Valid users authenticate under session policy while invalid, locked, expired, disabled, and forged sessions are denied server-side.
**In scope**: Password hashing and identity credential storage; platform accounts; login/lockout/session expiry/JWT; roles and menu/button/API/data permissions; association migration; login history and unusual-login event; production shared-console safe internal-error boundary and correlation display.
**Out of scope**: Tenant subaccounts, privileged plaintext reveal, balance/usage overview, and self-service password recovery not specified by the PRD; locked accounts use the PRD's audited administrator manual-unlock path.
**Depends on**: Phases 1-3.
**Requirements**: REQ-F-1-1, REQ-F-1-2, REQ-F-1-4.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner console-identity-platform-rbac --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Login, platform accounts, roles/permission tree, sessions/login history, shared safe internal-error state, auth/account/role APIs.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 05 --package console-identity-platform-rbac --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 05 --package console-identity-platform-rbac --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/05-console-identity-platform-rbac`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 05 --package console-identity-platform-rbac --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/05-console-identity-platform-rbac/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Valid users authenticate under session policy while invalid, locked, expired, disabled, and forged sessions are denied server-side.
2. Administrators manage accounts and four-granularity permissions and safely migrate users before role deletion.
3. Permission changes apply to pages, actions, APIs, and data without relying on frontend hiding.
4. A real server 500 renders the shared safe busy state with a correlation identity and no stack or sensitive detail; Phase 54 later assures trace/alert/log correlation.

**Test layers**: Password/session unit, database integration, authorization matrix, adversarial API, Playwright identity/RBAC.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 6: Privileged data access, operation audit, and security detection

**Package ID**: `privileged-data-access-audit`
**Goal**: Authorized users see masked data by default and every full reveal is current-RBAC checked, temporary, and audited.
**In scope**: Masked default views; role-checked temporary reveal; immutable sanitized operation audit; actor/tenant/resource/trace/IP/latency linkage; unusual login, repeated failure, bulk-export detection; security event handoff.
**Out of scope**: Generic alert notification and business-specific ledgers.
**Depends on**: Phases 3, and 5.
**Requirements**: REQ-F-14-1, REQ-F-14-2; atomic privileged-access obligations linked from the data-protection group.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner privileged-data-access-audit --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Audit/security-event search, detail drawers, reveal API, operation interceptors.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 06 --package privileged-data-access-audit --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 06 --package privileged-data-access-audit --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/06-privileged-data-access-audit`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 06 --package privileged-data-access-audit --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/06-privileged-data-access-audit/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Authorized users see masked data by default and every full reveal is current-RBAC checked, temporary, and audited.
2. Material operations are reconstructable without secret leakage or normal-application tampering.
3. Defined suspicious patterns emit attributable, deduplicated security events.

**Test layers**: Redaction/reveal unit, audit integrity integration, cross-tenant authorization, detection scenarios, Playwright.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 7: Platform system configuration

**Package ID**: `platform-system-configuration`
**Goal**: Authorized administrators can change typed platform settings without unsafe or silent runtime drift.
**In scope**: Production system-configuration API and Admin page; typed key registry; defaults; validation; secret classification; versioning; staged activation; safe hot reload; rollback; concurrency; audit and change evidence.
**Out of scope**: Business-domain policy editors already owned by their focused modules, identity/RBAC implementation, and observability assurance.
**Depends on**: Phases 2, 3, 5, and 6.
**Requirements**: Atomic `PROJECT-SYSTEM-CONFIG` obligations.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner platform-system-configuration --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Admin system-configuration list/editor/history, typed configuration API, reload/rollback boundary, audit events.
**UI contract**: Reuse Phase 2 tokens, shell, components, page/state registry, Pencil source, and HTML interaction source; inventory every production page/region/element/table/modal/drawer/popover/floating control/action/state/permission/full `data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 07 --package platform-system-configuration --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 07 --package platform-system-configuration --stage production` and require production PASS. Any production deviation updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX before reconciliation reruns.
**Entry gate**: Required inputs are every dependency's `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/07-platform-system-configuration`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 07 --package platform-system-configuration --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/07-platform-system-configuration/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. Any missing artifact, uncovered obligation, non-runnable command, contradiction, or BLOCKER exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Administrators see only typed authorized settings with defaults, validation, sensitivity, effective version, and change history.
2. Invalid, stale, or unauthorized changes cannot activate, while failed reload preserves the prior effective version.
3. Every activation and rollback is attributable, redacted, observable, and reproducible from evidence.

**Test layers**: Schema and policy unit, persistence/concurrency integration, authorization/security, reload/rollback fault, Playwright.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 8: Tenant qualification and status

**Package ID**: `tenant-qualification-status`
**Goal**: A tenant submits every required protected field/file and sees truthful certification feedback.
**In scope**: All qualification fields/files; platform-message contact verification; duplicate-code prevention; certification and supplement/reject/approve; maintenance/recertification; enable/disable/freeze with read-only history.
**Out of scope**: Trial consumption, credentials, signatures/templates, contracts, and termination.
**Depends on**: Phases 2, and 4-6.
**Requirements**: REQ-F-2-1, REQ-F-2-2, REQ-F-2-3, REQ-F-2-4.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner tenant-qualification-status --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Tenant qualification form/status, operator review/evidence workspace, tenant profile/status controls and APIs.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 08 --package tenant-qualification-status --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 08 --package tenant-qualification-status --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/08-tenant-qualification-status`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 08 --package tenant-qualification-status --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/08-tenant-qualification-status/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. A tenant submits every required protected field/file and sees truthful certification feedback.
2. Operators make traceable admission decisions and unqualified tenants cannot obtain sending resources.
3. Freeze/disable immediately rejects new work while authorized historical reads remain available.

**Test layers**: Field/state unit, file/database integration, authorization, system-message contract, Playwright onboarding/review/status.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 9: Tenant subaccounts and access credentials

**Package ID**: `tenant-access-administration`
**Goal**: Tenant administrators delegate only tenant-scoped access and cross-tenant access is denied.
**In scope**: Tenant roles/subaccounts and isolation; API-key create/one-time-secret/policy/revoke; downstream CMPP credential request/config/revoke; live revocation event.
**Out of scope**: HTTP HMAC request verification and CMPP protocol sessions.
**Depends on**: Phases 2, 5-6, and 8.
**Requirements**: REQ-F-1-3, REQ-F-2-6, REQ-F-2-7.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner tenant-access-administration --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Tenant administrator, API-key, CMPP access pages and credential APIs.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 09 --package tenant-access-administration --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 09 --package tenant-access-administration --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/09-tenant-access-administration`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 09 --package tenant-access-administration --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/09-tenant-access-administration/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Tenant administrators delegate only tenant-scoped access and cross-tenant access is denied.
2. API secrets are revealed once, protected at rest, policy-bound, and immediately revocable.
3. CMPP credentials expose permitted connection metadata without recoverable plaintext passwords.

**Test layers**: Role/credential unit, encryption integration, tenant isolation/adversarial API, Playwright.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 10: Channel configuration lifecycle

**Package ID**: `channel-configuration-lifecycle`
**Goal**: Only complete protected channel configurations can become effective.
**In scope**: Protocol/carrier/connectivity/credential/connection/price/priority/availability schema; validation and conformance adapter test; encrypted secrets; hot version reload/rollback; dependency inventory/migration; offline/delete.
**Out of scope**: Health scoring, pools, task migration, and real protocol interoperability.
**Depends on**: Phases 2-3, and 6.
**Requirements**: REQ-F-4-1, REQ-F-4-2, REQ-F-4-4.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner channel-configuration-lifecycle --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Channel list/form/detail, connectivity test, dependency migration wizard, configuration APIs/events.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 10 --package channel-configuration-lifecycle --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 10 --package channel-configuration-lifecycle --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/10-channel-configuration-lifecycle`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 10 --package channel-configuration-lifecycle --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/10-channel-configuration-lifecycle/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Only complete protected channel configurations can become effective.
2. A failed hot update preserves the prior effective version and provides evidence.
3. In-use channels cannot retire until every declared dependency is migrated.

**Test layers**: Schema/price unit, encryption/MySQL integration, conformance adapter, hot-reload rollback, Playwright.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 11: Channel health, pools, and candidate pause

**Package ID**: `channel-health-pools-candidate-pause`
**Goal**: Actual health transitions are visible and emit a source event.
**In scope**: Heartbeat/test health; connection/timeout/failure measures; maintenance event; weighted/primary-backup pools; manual/automatic pause; immediate candidate eviction; recovery test.
**Out of scope**: In-flight durable task ownership and migration, owned by Phase 23.
**Depends on**: Phases 2, and 10.
**Requirements**: REQ-F-4-3, REQ-F-4-6, REQ-F-4-7 (health/pool/candidate atomic obligations).
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner channel-health-pools-candidate-pause --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Channel monitor, health detail, pool editor, pause/recovery UI and health/pool APIs.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 11 --package channel-health-pools-candidate-pause --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 11 --package channel-health-pools-candidate-pause --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/11-channel-health-pools-candidate-pause`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 11 --package channel-health-pools-candidate-pause --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/11-channel-health-pools-candidate-pause/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Actual health transitions are visible and emit a source event.
2. Validated pools supply eligible candidates and paused channels receive no new routing assignments.
3. Recovery requires explicit successful evidence and preserves the pause/recovery audit.

**Test layers**: Health/pool/state unit, scheduler integration, candidate concurrency, authorization, Playwright.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 12: Signature lifecycle and channel filing

**Package ID**: `signature-lifecycle-filing`
**Goal**: Qualified tenants submit complete signatures and receive traceable review feedback.
**In scope**: Signature application/proof/risk; supplement/reject/approve; history; per-channel filing adapter/result/retry; available-channel calculation; disable contract.
**Out of scope**: Templates and runtime content safety.
**Depends on**: Phases 2, 8, and 10-11.
**Requirements**: REQ-F-3-1, REQ-F-3-2, REQ-F-3-3.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner signature-lifecycle-filing --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Tenant signature pages, operator review, filing matrix and APIs.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 12 --package signature-lifecycle-filing --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 12 --package signature-lifecycle-filing --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/12-signature-lifecycle-filing`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 12 --package signature-lifecycle-filing --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/12-signature-lifecycle-filing/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Qualified tenants submit complete signatures and receive traceable review feedback.
2. Operators review evidence/risk through valid state transitions.
3. Only successfully filed channels expose an approved signature as usable.

**Test layers**: State/risk unit, filing contract integration, authorization, Playwright application/review/filing.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 13: Template lifecycle and send compliance contract

**Package ID**: `template-lifecycle-compliance`
**Goal**: Template variable and content rules produce precise client/server outcomes.
**In scope**: Template fields/variables/rules/signature binding; review/resubmit; system-template governance; domestic free-text prohibition; shared pre-send validator and conformance kit for all ingress.
**Out of scope**: Runtime sensitive words and routing.
**Depends on**: Phases 2, 8, and 12.
**Requirements**: REQ-F-3-4, REQ-F-3-5, REQ-F-3-7.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner template-lifecycle-compliance --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Tenant template editor/preview, operator review, validation API/conformance kit.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 13 --package template-lifecycle-compliance --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 13 --package template-lifecycle-compliance --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/13-template-lifecycle-compliance`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 13 --package template-lifecycle-compliance --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/13-template-lifecycle-compliance/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Template variable and content rules produce precise client/server outcomes.
2. Review decisions and resubmission preserve history.
3. Every ingress conformance adapter rejects unapproved or mismatched domestic content identically.

**Test layers**: Parser/render/state unit, persistence, ingress conformance, API authorization, Playwright.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 14: Auditable exemption policy

**Package ID**: `auditable-exemption-policy`
**Goal**: Only authorized bounded exemptions can become effective.
**In scope**: Signature/content/account exemption types; explicit tenant/product/resource scope; approval, validity, precedence, revocation, and usage audit.
**Out of scope**: General resource review and runtime word authoring.
**Depends on**: Phases 2, 6, and 12-13.
**Requirements**: REQ-F-3-6.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner auditable-exemption-policy --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Exemption list/form/detail/history and policy API.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 14 --package auditable-exemption-policy --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 14 --package auditable-exemption-policy --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/14-auditable-exemption-policy`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 14 --package auditable-exemption-policy --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/14-auditable-exemption-policy/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Only authorized bounded exemptions can become effective.
2. Expired, revoked, unauthorized, or out-of-scope exemptions never alter decisions.
3. Every exemption decision/use cites actor, scope, and evidence.

**Test layers**: Policy/precedence unit, time/persistence integration, authorization, conformance, Playwright.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 15: Unified resource review history

**Package ID**: `resource-review-history`
**Goal**: Authorized operators can reconstruct signature, template, and exemption review decisions from one immutable production history.
**In scope**: Production Admin review-history query/detail page and API; cross-resource normalized decision records; filters; protected evidence access; prior-version links; decision actor/reason/time; tenant visibility linkage; audit.
**Out of scope**: Creating review decisions, filing, exemption evaluation, and generic operation-log search.
**Depends on**: Phases 2, 6, and 12-14.
**Requirements**: Atomic review-history behaviors; their machine-readable top-level integration links remain in `PRD-OBLIGATIONS.md` and do not change primary requirement ownership.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner resource-review-history --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Admin review-history list, filters, detail drawer, resource-version links, export entry handoff, review-history API.
**UI contract**: Reuse Phase 2 tokens, shell, components, page/state registry, Pencil source, and HTML interaction source; inventory every production page/region/element/table/modal/drawer/popover/floating control/action/state/permission/full `data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 15 --package resource-review-history --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 15 --package resource-review-history --stage production` and require production PASS. Any production deviation updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX before reconciliation reruns.
**Entry gate**: Required inputs are every dependency's `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/15-resource-review-history`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 15 --package resource-review-history --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/15-resource-review-history/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. Any missing artifact, uncovered obligation, non-runnable command, contradiction, or BLOCKER exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Operators filter a single production history by resource type, tenant, state, reviewer, risk, and decision time.
2. Each row opens the immutable submitted version, decision, reason, actor, evidence, and subsequent lifecycle link without leaking protected data.
3. Tenant-visible outcomes and Admin history reconcile to the same decision identity while unauthorized and cross-scope reads fail.

**Test layers**: Normalization/database, cross-resource integration, authorization/masking, API, accessibility, Playwright.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 16: Blacklist and third-party risk control

**Package ID**: `blacklist-risk-control`
**Goal**: Whitelist and three blacklist sources produce deterministic pre-task decisions with exact reasons.
**In scope**: System/tenant black/white lists; precedence; protected CRUD/import/export request; third-party single/batch provider, credentials/threshold/level, cache/timeout fallback; source trace; analysis/appeal.
**Out of scope**: Unsubscribe-origin creation and final export files.
**Depends on**: Phases 2, 6, and 8-9.
**Requirements**: REQ-F-5-1, REQ-F-5-2, REQ-F-5-3, REQ-F-5-4.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner blacklist-risk-control --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: List/provider/analysis pages, import UI, checker/provider APIs.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 16 --package blacklist-risk-control --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 16 --package blacklist-risk-control --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/16-blacklist-risk-control`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 16 --package blacklist-risk-control --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/16-blacklist-risk-control/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Whitelist and three blacklist sources produce deterministic pre-task decisions with exact reasons.
2. Provider failure follows configured fallback and records degraded evidence.
3. Authorized users manage and analyze list data without protected-number leakage.

**Test layers**: Precedence unit, Redis/provider integration, no-task route component, import/API authorization, Playwright.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 17: Runtime final-content safety

**Package ID**: `runtime-content-safety`
**Goal**: Content introduced only through variables is scanned in the final render.
**In scope**: Sensitive word/category/level/replacement/action/scope/state; CRUD/import/export request; final rendered content scan; normalization; block/replace/alert; precedence; hot update; metrics.
**Out of scope**: Template resource review and final export production.
**Depends on**: Phases 2, 13, and 16.
**Requirements**: REQ-F-5-5.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner runtime-content-safety --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Content policy pages and runtime checker/cache events.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 17 --package runtime-content-safety --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 17 --package runtime-content-safety --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/17-runtime-content-safety`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 17 --package runtime-content-safety --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/17-runtime-content-safety/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Content introduced only through variables is scanned in the final render.
2. Scoped policies deterministically block, replace, or alert with traceable matches.
3. Hot changes and real-decision metrics remain consistent.

**Test layers**: Matcher/normalization unit, Redis/MySQL integration, routing component, import/API, Playwright.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 18: Frequency and API rate controls

**Package ID**: `frequency-api-rate-controls`
**Goal**: Per-key multi-window limits are isolated and return the standard 429 contract.
**In scope**: Per-key second/minute/hour/day limits; number/tenant/IP/content-similarity rules; block/delay/alert; exemptions; Redis atomic counters; import/export request; standard 429.
**Out of scope**: Channel windows and final export production.
**Depends on**: Phases 2, 9, and 16.
**Requirements**: REQ-F-5-6, REQ-F-6-5.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner frequency-api-rate-controls --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Frequency/rate-rule pages, API-key limits, checker and 429 contract.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 18 --package frequency-api-rate-controls --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 18 --package frequency-api-rate-controls --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/18-frequency-api-rate-controls`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 18 --package frequency-api-rate-controls --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/18-frequency-api-rate-controls/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Per-key multi-window limits are isolated and return the standard 429 contract.
2. All rule dimensions behave consistently across instances.
3. Exemptions and hits are traceable and boundary-safe.

**Test layers**: Counter/similarity unit, real Redis concurrency, multi-instance component, API/load boundary, Playwright.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 19: Number attribution and portability

**Package ID**: `number-attribution-portability`
**Goal**: Prefix updates are validated, versioned, and recoverable.
**In scope**: Prefix import/incremental versions; carrier/province/city; portability provider/cache; protected numbers; fallback and source/freshness trace.
**Out of scope**: Route authoring and analytics.
**Depends on**: Phases 2-3, and 16.
**Requirements**: REQ-F-5-7, REQ-F-13-4.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner number-attribution-portability --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Prefix/import/version and portability query/config pages; attribution API.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 19 --package number-attribution-portability --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 19 --package number-attribution-portability --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/19-number-attribution-portability`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 19 --package number-attribution-portability --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/19-number-attribution-portability/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Prefix updates are validated, versioned, and recoverable.
2. Current portability results override prefix data only within policy.
3. Provider failure returns the correct source-labelled fallback.

**Test layers**: Prefix/normalization unit, provider/Redis/MySQL integration, route contract, import/API, Playwright.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 20: Provider status taxonomy and normalization

**Package ID**: `provider-status-taxonomy`
**Goal**: One unambiguous effective mapping supplies finality, billing, retry, and advice semantics.
**In scope**: Provider/protocol code taxonomy; platform finality, billability, retryability, severity, advice; effective versions; conflict/unknown fallback; import/export request; connector/billing/retry contract.
**Out of scope**: Actual provider receipts and final export files.
**Depends on**: Phases 2, and 10.
**Requirements**: REQ-F-13-3.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner provider-status-taxonomy --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Status mapping/version/import pages and normalization SPI/conformance fixtures.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 20 --package provider-status-taxonomy --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 20 --package provider-status-taxonomy --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/20-provider-status-taxonomy`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 20 --package provider-status-taxonomy --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/20-provider-status-taxonomy/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. One unambiguous effective mapping supplies finality, billing, retry, and advice semantics.
2. Unknown codes follow a safe explicit policy without rewriting history.
3. All downstream consumers use the same versioned normalization contract.

**Test layers**: Mapping/precedence unit, version integration, conformance fixtures, API/import authorization, Playwright.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 21: Routing, circuit, and retry policy

**Package ID**: `routing-circuit-policy`
**Goal**: Eligible context deterministically selects a rule/default target.
**In scope**: Ordered multi-condition routing/default; channel/pool weights; primary/backup; health circuit state; normalized-code retry rules; attempt caps; duplicate-contact guard; decision trace; conformance kit.
**Out of scope**: Network dispatch and in-flight migration.
**Depends on**: Phases 2, 11, and 16-20.
**Requirements**: REQ-F-5-8, REQ-F-5-9, REQ-F-5-10.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner routing-circuit-policy --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Rule builder/simulator, circuit/retry policy pages, routing API/events.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 21 --package routing-circuit-policy --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 21 --package routing-circuit-policy --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/21-routing-circuit-policy`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 21 --package routing-circuit-policy --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/21-routing-circuit-policy/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Eligible context deterministically selects a rule/default target.
2. Health-driven circuit transitions and recovery avoid unhealthy candidates and oscillation.
3. Normalized retry policy respects caps and duplicate-contact protection.

**Test layers**: Rule/weight/circuit/retry unit, Redis/MySQL concurrency, conformance, authorization, Playwright.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 22: Trial and prepaid ledger

**Package ID**: `trial-prepaid-ledger`
**Goal**: Trial sends consume quota and freeze correctly with explicit ledger evidence.
**In scope**: Trial activation/config/consume/freeze/convert request; explicit trial ledger; prepaid reserve/confirm/reverse; insufficient funds; consumption and immutable balance audit; concurrent idempotency.
**Out of scope**: Recharge approval, postpaid credit, statement, and final export file.
**Depends on**: Phases 2, 8, 13, and 21.
**Requirements**: REQ-F-2-8, REQ-F-8-1, REQ-F-8-4, REQ-F-8-9.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner trial-prepaid-ledger --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Trial status/config, quota/balance/consumption/audit pages and entitlement/billing APIs.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 22 --package trial-prepaid-ledger --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 22 --package trial-prepaid-ledger --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/22-trial-prepaid-ledger`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 22 --package trial-prepaid-ledger --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/22-trial-prepaid-ledger/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Trial sends consume quota and freeze correctly with explicit ledger evidence.
2. Prepaid reserve/confirm/reverse cannot overspend or double-apply under concurrency.
3. Every entitlement/balance mutation is linked, immutable, and authorized.

**Test layers**: Money/quota/state unit, real MySQL concurrency, crash/idempotency component, API, Playwright.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 23: Secure HTTP message acceptance

**Package ID**: `secure-http-message-acceptance`
**Goal**: Valid eligible requests return one stable message ID and every invalid boundary leaves no unauthorized task/charge.
**In scope**: Canonical body HMAC; protected secret; timestamp/Redis nonce/IP allow-list; schema; business idempotency; tenant/resource/risk/rate/attribution/routing/entitlement orchestration; durable submission/outbox/task; standard errors/trace.
**Out of scope**: Network provider dispatch, receipt, and batch.
**Depends on**: Phases 8-14, and 16-22.
**Requirements**: REQ-F-6-1, REQ-F-6-4, REQ-NFR-ERROR-IDEMPOTENCY.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner secure-http-message-acceptance --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: `POST /api/v1/sms/send`, shared acceptance service, outbox/task transaction.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/23-secure-http-message-acceptance`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 23 --package secure-http-message-acceptance --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/23-secure-http-message-acceptance/ENTRY-REVIEW.md`. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Valid eligible requests return one stable message ID and every invalid boundary leaves no unauthorized task/charge.
2. Concurrent/retried business requests cannot duplicate task, send intent, or ledger effects.
3. Dependency and crash outcomes are standard, traceable, and recoverable from durable state.

**Test layers**: Canonicalization/idempotency unit, MySQL/Redis integration, checker conformance, API security/concurrency.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD

### Phase 24: HTTP upstream delivery, receipt, and final status

**Package ID**: `http-upstream-delivery-closure`
**Goal**: A real/sandbox HTTP provider receives exactly one dispatch for an accepted task.
**In scope**: HTTP connector SPI implementation; protected credentials; dispatcher task claiming; provider idempotency; normalized response/receipt; final state; billing confirm/reverse; status query; submission detail; sandbox/real evidence.
**Out of scope**: In-flight migration policy, tenant callbacks, and CMPP.
**Depends on**: Phases 10, and 20-23.
**Requirements**: REQ-F-6-3, REQ-F-7-1; atomic real HTTP upstream acceptance obligations.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner http-upstream-delivery-closure --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Provider submit/receipt contracts, worker, status/submission APIs.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/24-http-upstream-delivery-closure`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 24 --package http-upstream-delivery-closure --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/24-http-upstream-delivery-closure/ENTRY-REVIEW.md`. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. A real/sandbox HTTP provider receives exactly one dispatch for an accepted task.
2. Duplicate/out-of-order receipts apply one normalized final state and one financial effect.
3. Authorized clients query status/submission trace while cross-tenant reads fail.

**Test layers**: Connector/status unit, provider contract, worker concurrency, receipt/billing component, API authorization, real E2E.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD

### Phase 25: Durable dispatch task migration and recovery

**Package ID**: `dispatch-task-migration-recovery`
**Goal**: Paused channels receive no new work and eligible unsubmitted tasks migrate once to a valid fallback.
**In scope**: Task ownership/lease; paused/failed-channel in-flight inventory; safe release/reselect; uncertain provider outcome; compensation; no-backup behavior; recovery test; operator evidence.
**Out of scope**: Initial health/pool configuration and new transport protocols.
**Depends on**: Phases 11, 21, and 23-24.
**Requirements**: Atomic in-flight migration obligations linked from the channel-pause and circuit-routing groups.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner dispatch-task-migration-recovery --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Migration/recovery worker, task migration inventory and operator recovery page/API.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 25 --package dispatch-task-migration-recovery --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 25 --package dispatch-task-migration-recovery --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/25-dispatch-task-migration-recovery`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 25 --package dispatch-task-migration-recovery --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/25-dispatch-task-migration-recovery/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Paused channels receive no new work and eligible unsubmitted tasks migrate once to a valid fallback.
2. Uncertain provider outcomes are quarantined/reconciled rather than blindly duplicated.
3. No-backup and recovery failures remain visible, recoverable, and audited.

**Test layers**: Lease/state unit, MySQL/Redis concurrency, crash/fault component, Playwright migration/recovery.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 26: Authenticated tenant console send

**Package ID**: `tenant-console-send`
**Goal**: Users select only tenant-approved resources and see exact final-render preview.
**In scope**: JWT console adapter; tenant resource selection; variable form; masked recipients; preview; quota/balance feedback; single/small-batch submit; idempotent double-click; production network-timeout/retry state bound to Phase 23 idempotency and correlation identity.
**Out of scope**: File bulk imports and HMAC credential flows.
**Depends on**: Phases 2, 5, 13, and 22-24.
**Requirements**: REQ-F-6-10.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner tenant-console-send --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Tenant send page, network-timeout/retry state, and JWT console-send API.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 26 --package tenant-console-send --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 26 --package tenant-console-send --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/26-tenant-console-send`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 26 --package tenant-console-send --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/26-tenant-console-send/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Users select only tenant-approved resources and see exact final-render preview.
2. Console submission follows the same compliance/routing/billing/delivery contract as HTTP acceptance.
3. Invalid, stale, unauthorized, repeated, and partial results show actionable safe feedback.
4. A network timeout shows the specified safe message and retry action; repeated retry cannot duplicate the Phase 23 task or financial effect and retains a correlation identity.

**Test layers**: Form/component, adapter parity integration, idempotent timeout/retry integration, authorization, Playwright browser-to-final-status.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 27: Message, receipt, and error operations

**Package ID**: `message-receipt-error-operations`
**Goal**: Authorized users trace messages without tenant or sensitive-data leakage.
**In scope**: Full filters/columns; masked/reveal data; timeline; normalized errors; safe resend/appeal/problem mark; receipt correction/replay; original evidence; export request handoff.
**Out of scope**: Export file generation and callback failures.
**Depends on**: Phases 2, 6, 20, and 23-25.
**Requirements**: REQ-F-7-2, REQ-F-7-4, REQ-F-7-6 (query/detail/action atomic obligations).
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner message-receipt-error-operations --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Send, receipt, error lists/details/actions and APIs.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 27 --package message-receipt-error-operations --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 27 --package message-receipt-error-operations --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/27-message-receipt-error-operations`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 27 --package message-receipt-error-operations --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/27-message-receipt-error-operations/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Authorized users trace messages without tenant or sensitive-data leakage.
2. Resend/correction/replay actions are eligible, idempotent, reasoned, and preserve original evidence.
3. Export requests hand off explicit snapshots to the export owner without claiming file completion.

**Test layers**: Query/action unit, MySQL integration, authorization/idempotency API, Playwright.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 28: Generic Webhook delivery transport

**Package ID**: `webhook-delivery-transport`
**Goal**: Status events reach configured secure endpoints as signed idempotent callbacks.
**In scope**: Tenant callback configuration; versioned signed/idempotent envelope; secure destination/SSRF controls; delivery queue; backoff; attempt history; manual replay; pause/resume; failure monitor; reusable event-type adapter.
**Out of scope**: Uplink domain normalization and unsubscribe policy.
**Depends on**: Phases 2, 6, 9, and 23-24.
**Requirements**: REQ-F-6-6, REQ-F-7-7.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner webhook-delivery-transport --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Callback configuration, failed-push operations, Webhook payload/signature contract.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 28 --package webhook-delivery-transport --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 28 --package webhook-delivery-transport --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/28-webhook-delivery-transport`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 28 --package webhook-delivery-transport --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/28-webhook-delivery-transport/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Status events reach configured secure endpoints as signed idempotent callbacks.
2. Failures follow declared retry/terminal behavior and retain every attempt.
3. Authorized replay/pause operates on the reusable transport without domain duplication.

**Test layers**: Payload/signature unit, WireMock retry, queue concurrency, SSRF/API security, Playwright.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 29: Bulk, scheduled, and task operations

**Package ID**: `bulk-scheduled-task-operations`
**Goal**: Every valid item receives stable identity and reuses single-message acceptance.
**In scope**: Bulk API; secure Excel/CSV import and mapping/partial failures; per-recipient variables; batch/item IDs; schedule/priority; pause/resume/cancel/restart; task console; item-derived status/cost; acceptance reuse.
**Out of scope**: Export center and new compliance logic.
**Depends on**: Phases 2, 6, and 23-27.
**Requirements**: REQ-F-6-2, REQ-F-6-11, REQ-F-6-12, REQ-F-7-3, REQ-F-13-5.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner bulk-scheduled-task-operations --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Bulk API, tenant import/preview, task/batch lists/details/control and operator console.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 29 --package bulk-scheduled-task-operations --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 29 --package bulk-scheduled-task-operations --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/29-bulk-scheduled-task-operations`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 29 --package bulk-scheduled-task-operations --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/29-bulk-scheduled-task-operations/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Every valid item receives stable identity and reuses single-message acceptance.
2. Task totals/status/cost derive from item truth and expose partial failure.
3. Concurrent task controls have explicit idempotent boundaries.

**Test layers**: Parser/state unit, malware/content validation, MySQL/queue integration, worker concurrency, API, Playwright.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 30: Upstream CMPP connector

**Package ID**: `upstream-cmpp-connector`
**Goal**: The connector interoperates with a real gateway or authoritative simulator.
**In scope**: CMPP client codec; auth; window/sequence; segmentation/encoding; heartbeat/reconnect; submit/status/uplink; task claiming; normalized outcomes; connector conformance; real/simulator interoperability.
**Out of scope**: Downstream CMPP server and SGIP/SMGP implementations.
**Depends on**: Phases 10, 20-21, and 23-25.
**Requirements**: Atomic real CMPP upstream acceptance obligations.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner upstream-cmpp-connector --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: CMPP CONNECT/SUBMIT/DELIVER/ACTIVE_TEST/TERMINATE client flows and captures.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/30-upstream-cmpp-connector`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 30 --package upstream-cmpp-connector --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/30-upstream-cmpp-connector/ENTRY-REVIEW.md`. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. The connector interoperates with a real gateway or authoritative simulator.
2. Disconnect/reconnect preserves task ownership and uncertain outcomes without duplicate contact/charge.
3. Responses, receipts, and uplinks normalize into the shared platform contracts.

**Test layers**: Codec vectors/property, fragmented TCP/fault integration, simulator contract, billing/receipt component, interoperability E2E.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD

### Phase 31: Downstream CMPP gateway

**Package ID**: `downstream-cmpp-gateway`
**Goal**: Permitted clients authenticate and malformed/unauthorized flows receive precise outcomes.
**In scope**: CMPP server; account/IP auth; sessions/connections/windows/TPS; protocol lifecycle; Service_Id/TLV binding; shared acceptance; precise responses; status/uplink deliver; revocation and durable pending reports.
**Out of scope**: Separate routing/billing/compliance paths.
**Depends on**: Phases 9, 13, 20, 23-24, 28, and 30.
**Requirements**: REQ-F-6-7, REQ-F-6-8, REQ-F-6-9.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner downstream-cmpp-gateway --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: CMPP server protocol flows and tenant credential/session contracts.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/31-downstream-cmpp-gateway`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 31 --package downstream-cmpp-gateway --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/31-downstream-cmpp-gateway/ENTRY-REVIEW.md`. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Permitted clients authenticate and malformed/unauthorized flows receive precise outcomes.
2. All submits reuse shared compliance/acceptance and cannot bypass tenant policy.
3. Requested reports/uplinks reach the correct session or durable queue without leakage.

**Test layers**: Codec/session unit, TCP integration, authoritative client contract, acceptance parity, security/fault E2E.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD

### Phase 32: Uplink normalization, search, and push

**Package ID**: `uplink-normalization-operations`
**Goal**: Uplinks from all connectors normalize to one source-labelled tenant-scoped record.
**In scope**: HTTP/CMPP uplink normalization; tenant/signature/product correlation; platform/tenant search/detail; keyword/auto-reply hook; generic Webhook/CMPP push adapters; replay and push reliability monitor.
**Out of scope**: Unsubscribe keyword policy and suppression evidence.
**Depends on**: Phases 2, 6, 19, 24, 28, and 30-31.
**Requirements**: REQ-F-7-5, REQ-F-10-1, REQ-F-10-4.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner uplink-normalization-operations --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Uplink lists/details/search/replay, push monitor, normalization/push APIs.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 32 --package uplink-normalization-operations --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 32 --package uplink-normalization-operations --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/32-uplink-normalization-operations`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 32 --package uplink-normalization-operations --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/32-uplink-normalization-operations/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Uplinks from all connectors normalize to one source-labelled tenant-scoped record.
2. Authorized users search/replay without cross-tenant leakage.
3. Push uses generic transport/CMPP adapters and exposes durable success/failure evidence.

**Test layers**: Normalization/correlation unit, MySQL integration, callback/CMPP component, authorization, Playwright.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 33: Unsubscribe suppression, evidence, and statistics

**Package ID**: `unsubscribe-compliance`
**Goal**: A matching uplink creates exactly one tenant suppression and linked evidence record.
**In scope**: Global/tenant keyword library; normalization; atomic tenant blacklist insertion; one unsubscribe evidence record; optional confirmation; tenant notification; search/export request; real counts/rates and alert event.
**Out of scope**: Generic uplink transport, export file creation, and alert delivery.
**Depends on**: Phases 2, 13, 16, 28, and 32.
**Requirements**: REQ-F-7-9, REQ-F-10-2, REQ-F-10-3.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner unsubscribe-compliance --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Keyword configuration, unsubscribe list/detail, statistics and APIs.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 33 --package unsubscribe-compliance --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 33 --package unsubscribe-compliance --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/33-unsubscribe-compliance`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 33 --package unsubscribe-compliance --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/33-unsubscribe-compliance/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. A matching uplink creates exactly one tenant suppression and linked evidence record.
2. Subsequent tenant sends are blocked and tenant notification/optional confirmation is traceable.
3. Search and statistics use real evidence; export requests hand off a defined snapshot.

**Test layers**: Keyword/idempotency unit, MySQL/blacklist integration, transport component, authorization, Playwright.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 34: Core statistics aggregation pipeline

**Package ID**: `statistics-aggregation-pipeline`
**Goal**: Aggregates reconcile to source records under versioned formulas and permissions.
**In scope**: Canonical metric/source/formula/freshness/permission registry; event ingestion; final message/receipt/billing/resource/tenant/channel aggregates; timezone and late/corrected data; idempotent rebuild; drill-down keys; accessible data-table feeds.
**Out of scope**: Custom report authoring, dashboards, complaint ratio, and finance-specific profit.
**Depends on**: Phases 19-20, 24, 27, 29, and 32-33.
**Requirements**: REQ-F-3-8, REQ-F-4-5, REQ-F-11-1, REQ-F-11-2, REQ-F-11-3.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner statistics-aggregation-pipeline --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Aggregation jobs/tables, metric registry/API, reconciliation reports.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/34-statistics-aggregation-pipeline`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 34 --package statistics-aggregation-pipeline --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/34-statistics-aggregation-pipeline/ENTRY-REVIEW.md`. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Aggregates reconcile to source records under versioned formulas and permissions.
2. Duplicate, late, and corrected events update without double count.
3. Every metric exposes source, freshness, quality, and drill-down identity.

**Test layers**: Formula unit, event/MySQL integration, rebuild/correction/idempotency, authorization reconciliation.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD

### Phase 35: Alert engine, notification routing, and console

**Package ID**: `alert-engine-console`
**Goal**: A real source breach creates one classified alert episode and routed delivery evidence.
**In scope**: Typed source-backed rules; threshold/duration/severity; episodes/dedupe/recovery; platform system-message/email/DingTalk/WeCom adapters; notification destination/inbox; attempts; active/ack/resolved/mute; dashboard/history.
**Out of scope**: Creation of every business source metric.
**Depends on**: Phases 2, 4, 6, 11, 24, 28, and 34.
**Requirements**: REQ-F-11-7, REQ-F-12-1, REQ-F-12-2, REQ-F-12-3.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner alert-engine-console --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Alert rule/notification settings, notification inbox, alert dashboard/list/detail and APIs/events.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 35 --package alert-engine-console --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 35 --package alert-engine-console --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/35-alert-engine-console`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 35 --package alert-engine-console --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/35-alert-engine-console/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. A real source breach creates one classified alert episode and routed delivery evidence.
2. Operators acknowledge, resolve, and mute without state corruption or hidden alerts.
3. Adapter failure is visible/retriable and never loses the alert episode.

**Test layers**: Rule/state/dedupe unit, event integration, adapter fault contract, authorization, Playwright.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 36: Tenant recharge operations

**Package ID**: `tenant-recharge-operations`
**Goal**: Tenants submit complete protected recharge evidence and see truthful state.
**In scope**: Recharge request fields/proof; finance review; transaction uniqueness; exactly-once balance credit; rejection/failure; audit and histories.
**Out of scope**: Payment gateway automation and consumption charging.
**Depends on**: Phases 2-3, 6, and 22.
**Requirements**: REQ-F-8-3.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner tenant-recharge-operations --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Tenant recharge and finance review pages/APIs.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 36 --package tenant-recharge-operations --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 36 --package tenant-recharge-operations --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/36-tenant-recharge-operations`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 36 --package tenant-recharge-operations --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/36-tenant-recharge-operations/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Tenants submit complete protected recharge evidence and see truthful state.
2. Finance decisions credit exactly once or reject without balance mutation.
3. Duplicate/concurrent decisions fail safely and remain auditable.

**Test layers**: Money/state unit, MySQL concurrency/unique integration, authorization, Playwright.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 37: Contract pricing and postpaid credit

**Package ID**: `contract-pricing-postpaid`
**Goal**: Approved conversion binds an immutable effective contract/price version.
**In scope**: Trial conversion; contract fields/files; immutable price-book versions/tiers; billing mode; credit/billing period; postpaid usage; over-credit block/manual approval hook.
**Out of scope**: Statements, settlement, and fee warnings.
**Depends on**: Phases 2, 8, 22-24, and 36.
**Requirements**: REQ-F-2-5, REQ-F-2-9, REQ-F-8-2.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner contract-pricing-postpaid --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Conversion, contract review, price book, billing/credit pages and APIs.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 37 --package contract-pricing-postpaid --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 37 --package contract-pricing-postpaid --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/37-contract-pricing-postpaid`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 37 --package contract-pricing-postpaid --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/37-contract-pricing-postpaid/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Approved conversion binds an immutable effective contract/price version.
2. Every accepted message uses the effective billing mode/price without retroactive drift.
3. Concurrent postpaid use respects credit and selected over-credit action.

**Test layers**: Price/credit/state unit, effective-version/MySQL concurrency, acceptance component, authorization, Playwright.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 38: Reconciliation, settlement, and invoices

**Package ID**: `reconciliation-settlement-invoices`
**Goal**: Statements reconcile to message/billing sources and preserve differences.
**In scope**: Source-backed statements; tenant/finance confirmation; difference evidence/resolution; settlement/payment states; invoice request/issue/status; export request hook.
**Out of scope**: Profit analytics, fee warnings, and export files.
**Depends on**: Phases 2, 22, 27, 34, and 37.
**Requirements**: REQ-F-8-5, REQ-F-8-6, REQ-F-8-7.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner reconciliation-settlement-invoices --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Statement/difference, settlement, invoice pages and APIs.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 38 --package reconciliation-settlement-invoices --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 38 --package reconciliation-settlement-invoices --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/38-reconciliation-settlement-invoices`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 38 --package reconciliation-settlement-invoices --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/38-reconciliation-settlement-invoices/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Statements reconcile to message/billing sources and preserve differences.
2. Settlement state advances exactly once after resolved evidence.
3. Invoices cannot exceed eligible amounts and remain traceable.

**Test layers**: Calculation/state unit, real-ledger integration, concurrent API authorization, Playwright.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 39: Financial source analytics

**Package ID**: `financial-source-analytics`
**Goal**: Displayed cost/revenue/profit reconciles to provider cost, price version, and billable messages.
**In scope**: Actual upstream cost; effective tenant revenue; billable final messages; channel/tenant/period cost, revenue, profit; correction/reversal handling; source drill-down.
**Out of scope**: Threshold warnings, credit actions, and generic reports.
**Depends on**: Phases 2, 20, 24, 34, and 37-38.
**Requirements**: REQ-F-8-8.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner financial-source-analytics --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Finance cost/profit reports, source/formula registry entries and APIs.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 39 --package financial-source-analytics --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 39 --package financial-source-analytics --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/39-financial-source-analytics`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 39 --package financial-source-analytics --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/39-financial-source-analytics/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Displayed cost/revenue/profit reconciles to provider cost, price version, and billable messages.
2. Receipt corrections and reversals update analytics idempotently.
3. Authorized drill-down reaches exact financial and message sources.

**Test layers**: Formula unit, real-ledger aggregate integration, correction/idempotency, authorization, Playwright.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 40: Fee warning and credit enforcement

**Package ID**: `fee-warning-credit-enforcement`
**Goal**: Real balance/credit sources trigger one warning episode under configured rules.
**In scope**: Prepaid amount/estimated-use warnings; postpaid credit ratios; thresholds/recipients/channels; episode dedupe; tenant/finance feedback; automatic block or manual approval; acceptance integration.
**Out of scope**: Financial source calculation and generic alert lifecycle.
**Depends on**: Phases 2, 22, 35, and 37-39.
**Requirements**: REQ-F-8-10.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner fee-warning-credit-enforcement --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Fee-warning rules/history, tenant notice, manual approval queue and APIs/events.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 40 --package fee-warning-credit-enforcement --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 40 --package fee-warning-credit-enforcement --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/40-fee-warning-credit-enforcement`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 40 --package fee-warning-credit-enforcement --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/40-fee-warning-credit-enforcement/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Real balance/credit sources trigger one warning episode under configured rules.
2. Notifications reuse the alert transport and expose delivery evidence.
3. Configured block/manual approval is enforced consistently by acceptance.

**Test layers**: Rule unit, ledger/alert integration, acceptance race component, authorization, Playwright.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 41: Complaint case management

**Package ID**: `complaint-case-management`
**Goal**: Complaints retain every available link and explicit unknown attribution.
**In scope**: Complaint source/intake; nullable links/data quality; state flow; opinions/remediation; blacklist and tenant/signature/template/channel pause actions; active-task participant call; trends/distribution.
**Out of scope**: Automatic tenant threshold policy and ratio dashboard.
**Depends on**: Phases 2, 6, 8, 11-13, 16, 25, 27, and 35.
**Requirements**: REQ-F-9-1, REQ-F-9-2, REQ-F-9-3, REQ-F-9-4.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner complaint-case-management --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Complaint list/intake/detail/timeline/remediation/report and APIs.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 41 --package complaint-case-management --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 41 --package complaint-case-management --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/41-complaint-case-management`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 41 --package complaint-case-management --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/41-complaint-case-management/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Complaints retain every available link and explicit unknown attribution.
2. Valid state transitions preserve evidence and actor history.
3. Remediation targets exact resources and invokes safe task handling without duplicate action.

**Test layers**: State/action unit, real-message integration, concurrent remediation, authorization, Playwright.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 42: Tenant risk warning and auto-pause

**Package ID**: `tenant-risk-auto-pause`
**Goal**: Incomplete source data displays unknown rather than a misleading safe rate.
**In scope**: Complaint/failure/unsubscribe rates from source registry; threshold/duration/data quality; alert episode; auto-pause; preserved read/reconciliation; reviewed recovery and action log.
**Out of scope**: Source aggregate creation and generic alert transport.
**Depends on**: Phases 2, 8, 33-35, and 40-41.
**Requirements**: REQ-F-9-5.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner tenant-risk-auto-pause --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Tenant risk rules/history/pause/recovery pages and APIs.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 42 --package tenant-risk-auto-pause --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 42 --package tenant-risk-auto-pause --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/42-tenant-risk-auto-pause`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 42 --package tenant-risk-auto-pause --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/42-tenant-risk-auto-pause/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Incomplete source data displays unknown rather than a misleading safe rate.
2. Sustained breach produces one alert and optional pause that blocks sends but preserves reads.
3. Recovery requires authorized evidence and remains linked to the episode.

**Test layers**: Window/rule unit, aggregate/status integration, scheduler concurrency, authorization, Playwright.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 43: Custom report authoring

**Package ID**: `custom-report-authoring`
**Goal**: Users compose only supported authorized dimensions/measures with explicit formulas.
**In scope**: Supported dimension/measure registry; visual report builder; validation; saved definitions; tenant/role scope; source/freshness/quality; accessible table alternative; export request.
**Out of scope**: Core aggregation, dashboards, and export file creation.
**Depends on**: Phases 2, 34, and 39.
**Requirements**: REQ-F-11-4.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner custom-report-authoring --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Custom report builder/view/save pages and query APIs.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 43 --package custom-report-authoring --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 43 --package custom-report-authoring --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/43-custom-report-authoring`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 43 --package custom-report-authoring --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/43-custom-report-authoring/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Users compose only supported authorized dimensions/measures with explicit formulas.
2. Results reconcile to core aggregates and expose freshness/quality/drill-down.
3. Saved reports and export requests preserve immutable definitions/snapshots.

**Test layers**: Query-builder unit, aggregate integration, authorization, accessibility, Playwright.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 44: Operational dashboards and account overview

**Package ID**: `operational-dashboards`
**Goal**: All role dashboards use real source-backed metrics and expose freshness/error states.
**In scope**: Platform realtime KPI/trend/activity; operations KPI/rank/channel health; production Admin API-status monitoring for API/service/queue/provider/database/cache/callback health; production channel/tenant/signature-template resource statistics consuming Phase 34 aggregates; tenant identity/balance/usage/service overview; manual/poll refresh; card visibility; formula/freshness/permission registry; role-specific views.
**Out of scope**: Complaint ratio cards and drag layout.
**Depends on**: Phases 2, 5, 22, 24, 34-35, and 39.
**Requirements**: REQ-F-1-5, REQ-F-11-5, REQ-F-11-6, REQ-F-11-10; the atomic API-status UI peer obligation is linked through `PRD-OBLIGATIONS.md` without changing the observability requirement's primary owner.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner operational-dashboards --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Admin realtime/operations dashboards, channel/tenant/resource statistics, production API-status monitor and drill-down, tenant overview/template statistics, dashboard configuration and APIs.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 44 --package operational-dashboards --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 44 --package operational-dashboards --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/44-operational-dashboards`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 44 --package operational-dashboards --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/44-operational-dashboards/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. All role dashboards use real source-backed metrics and expose freshness/error states.
2. Tenant overview combines identity, balance, usage, and service data without cross-tenant leakage.
3. Admin configuration controls supported cards/formulas/refresh, the API-status page exposes source/freshness/impact/drill-down, and channel/tenant/resource statistics pages consume Phase 34 aggregates with permission, formula, freshness, empty, error, and accessible-table states.

**Test layers**: Selector/formula unit, aggregate integration, authorization/cache, accessibility/visual, Playwright.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 45: Complaint-ratio dashboard and intervention

**Package ID**: `complaint-ratio-intervention`
**Goal**: Ratios reconcile exactly to source sends/complaints with explicit unknown/zero behavior.
**In scope**: Real send denominator/complaint numerator; null/zero/data quality; threshold versions; month selection/freshness; exceeded sorting/highlight; Top N/all; drill-down; channel/tenant pause and alert.
**Out of scope**: Complaint intake, pause internals, and generic alerts.
**Depends on**: Phases 11, 25, 34-35, and 41-44.
**Requirements**: REQ-F-11-9.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner complaint-ratio-intervention --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Channel/tenant ratio cards/lists, drill-down and pause confirmation APIs/UI.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 45 --package complaint-ratio-intervention --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 45 --package complaint-ratio-intervention --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/45-complaint-ratio-intervention`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 45 --package complaint-ratio-intervention --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/45-complaint-ratio-intervention/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Required product decision**: `DECISIONS.md` records the product owner's confirmation of the source and configured default for the PRD's 0.3 percent complaint-ratio threshold, or an approved replacement value/version. Missing decision evidence blocks implementation; configurability does not waive confirmation.
**Success Criteria** (observable truths):

1. Ratios reconcile exactly to source sends/complaints with explicit unknown/zero behavior.
2. Threshold ordering/highlight and historical selection use the correct version/freshness.
3. Drill-down, alert, and pause target the exact dimension and preserve evidence.

**Test layers**: Ratio/boundary unit, aggregate/complaint integration, alert/pause component, authorization, Playwright.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 46: Secure asynchronous export

**Package ID**: `secure-async-export`
**Goal**: All export entry points create one authorized immutable snapshot and a downloadable artifact that reconciles exactly to its permitted source rows.
**In scope**: Export types/formats; immutable authorization-filter snapshot; artifact header/row/type/order/source reconciliation; masking inside the produced file; malware/content rules for inputs where reused; async split/queue/retry/partial failure; progress/metadata; encrypted/password artifact; expiring download; audit; all producer handoffs.
**Out of scope**: Retention/archive/restore.
**Depends on**: Phases 2-3, 6, 16-18, 20, 27, 29, 33, 38, and 43.
**Requirements**: REQ-F-7-8; export-file atomic obligations from F-5, F-7, F-8, F-10, F-11, and F-14.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner secure-async-export --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Export center, launch handoffs, job detail/retry/download APIs and UI.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 46 --package secure-async-export --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 46 --package secure-async-export --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/46-secure-async-export`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 46 --package secure-async-export --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/46-secure-async-export/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. All export entry points create one authorized immutable snapshot and job.
2. Large/partial/failing jobs retry safely and expose accurate item/file state.
3. Each supported producer/format artifact is parsed in acceptance tests and reconciles headers, row count, typed values, order, authorization filter, and masked protected fields before encrypted, expiring, role-checked, audited download.

**Test layers**: Format/parser and snapshot unit, source-to-artifact reconciliation, object-store/queue integration, authorization/fault, producer conformance, Playwright.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 47: Retention, archive, and restore

**Package ID**: `retention-archive-restore`
**Goal**: Eligible hot data moves to encrypted checksummed archives without loss.
**In scope**: Hot partition policy; retention/legal holds; encrypted cold archive manifest/checksum; archive retry/reconciliation; search; restore; export integration; corruption detection; deletion eligibility and audit.
**Out of scope**: Ordinary export job creation and production HA.
**Depends on**: Phases 1, 3, 6, 24, 27, 32-34, 38, and 46.
**Requirements**: REQ-NFR-RETENTION.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner retention-archive-restore --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Archive policy/manifest/search/restore administration, jobs and evidence.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 47 --package retention-archive-restore --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 47 --package retention-archive-restore --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/47-retention-archive-restore`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 47 --package retention-archive-restore --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/47-retention-archive-restore/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Eligible hot data moves to encrypted checksummed archives without loss.
2. Authorized historical search/restore/export verifies manifest and source identity.
3. Corruption, partial archive, hold, and deletion failures are detected and recoverable.

**Test layers**: Policy/manifest unit, object-store/MySQL integration, corruption/fault drills, authorization, Playwright.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 48: Short-link creation and safety review

**Package ID**: `shortlink-safety-review`
**Goal**: Pre-approval/private/unsafe targets never redirect or abuse server networking.
**In scope**: URL/domain/validity; SSRF/DNS rebinding sandbox; code; pending interstitial; automated evidence/screenshot/risk/manual review; immutable target; approve/reject/expire/offline; target巡检/alert; click analytics; export request.
**Out of scope**: Marketing automation.
**Depends on**: Phases 2-6, 8, 34-35, and 46.
**Requirements**: REQ-F-13-1, REQ-F-13-2.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner shortlink-safety-review --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Tenant short-link, operator review, public redirect states,巡检 and analytics.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 48 --package shortlink-safety-review --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 48 --package shortlink-safety-review --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/48-shortlink-safety-review`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 48 --package shortlink-safety-review --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/48-shortlink-safety-review/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Pre-approval/private/unsafe targets never redirect or abuse server networking.
2. Automated evidence and human review control immutable target availability.
3. Target drift takes links offline with alert; valid clicks produce privacy-safe analytics.

**Test layers**: URL/risk unit, sandbox DNS/HTTP integration, SSRF/TOCTOU security, analytics, Playwright.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 49: Tenant cooperation termination

**Package ID**: `tenant-cooperation-termination`
**Goal**: Incomplete participants or financial clearance block termination with exact evidence.
**In scope**: Machine-readable participant inventory; request/reason/evidence; finance clearance/refund/settlement; approval; active batch/task/session/callback/uplink/unsubscribe handling; credential/resource revocation; irreversible state; compensation/failure; retained reads/audit.
**Out of scope**: Premature data deletion.
**Depends on**: Phases 2, 5-6, 8-14, 22-33, 35-38, 41-42, and 46-47.
**Requirements**: REQ-F-2-10.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner tenant-cooperation-termination --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Termination request, clearance checklist, participant status, approval/confirmation/timeline and orchestrator.
**UI contract**: Reuse Phase 2 tokens/shell/components; map stable page/state IDs across Pencil, HTML, and React; inventory every page/region/element/table/modal/drawer/popover/floating control/action/state/permission/`data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 49 --package tenant-cooperation-termination --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 49 --package tenant-cooperation-termination --stage production` and require production PASS. Any drift updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX and reruns reconciliation.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/49-tenant-cooperation-termination`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 49 --package tenant-cooperation-termination --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/49-tenant-cooperation-termination/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Incomplete participants or financial clearance block termination with exact evidence.
2. Effective termination atomically rejects every ingress, closes sessions/work, revokes credentials/resources, and records compensation outcomes.
3. Historical detail, finance, audit, and retained evidence remain authorized and queryable.

**Test layers**: State/orchestration unit, participant contract tests, MySQL/session/queue fault/race, protocol/API authorization, Playwright.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 50: Tenant help and developer center

**Package ID**: `tenant-help-center`
**Goal**: Tenant users can reach versioned guidance, implemented API documentation, and a truthful customer-service contact path.
**In scope**: Production Tenant help shell; searchable usage guide; versioned HTTP API documentation generated/reconciled from the implemented contract; examples and error references; customer-service availability/destination/fallback; permissions; accessibility; content version evidence.
**Out of scope**: A full customer-service ticketing product, undocumented future APIs, and business feature implementation.
**Depends on**: Phases 2, 5, 9, and 23.
**Requirements**: Atomic `PROJECT-TENANT-HELP` obligations; the developer-documentation integration link is machine-readable in `PRD-OBLIGATIONS.md` and does not change primary requirement ownership.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner tenant-help-center --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Tenant usage-guide page, API documentation page, customer-service entry and fallback, content/version registry.
**UI contract**: Reuse Phase 2 tokens, shell, components, page/state registry, Pencil source, and HTML interaction source; inventory every production page/region/element/table/modal/drawer/popover/floating control/action/state/permission/full `data-testid`; run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 50 --package tenant-help-center --stage design`. This is the design entry gate. Before TODO-empty/commit, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 50 --package tenant-help-center --stage production` and require production PASS. Any production deviation updates UI-ELEMENTS, DECISIONS, and TEST-MATRIX before reconciliation reruns.
**Entry gate**: Required inputs are every dependency's `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/50-tenant-help-center`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 50 --package tenant-help-center --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/50-tenant-help-center/ENTRY-REVIEW.md --ui`. Here `--ui` invokes only UI `--stage design`; React and executed production evidence are exit requirements. Any missing artifact, uncovered obligation, non-runnable command, contradiction, or BLOCKER exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Tenant users search and navigate a versioned guide that matches the production portal and current permissions.
2. Published API requests, authentication, errors, and examples reconcile mechanically to the implemented versioned HTTP contract.
3. The customer-service entry exposes current availability and destination and provides an actionable fallback when contact delivery is unavailable.

**Test layers**: Contract/document drift, link/example execution, permissions, accessibility, visual, Playwright.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD
**UI hint**: yes

### Phase 51: Security assurance

**Package ID**: `security-assurance`
**Goal**: Adversarial tests prove all role, tenant, credential, protocol, and network boundaries.
**In scope**: Full TLS/mTLS boundary; JWT/RBAC/HMAC/CMPP adversarial matrix; OWASP/API threats; tenant isolation; replay/SSRF/import/export/security headers; secret/data leakage; remediation and recheck.
**Out of scope**: Performance, HA, observability, extension, and release composition.
**Depends on**: Phases 1-50.
**Requirements**: REQ-NFR-SECURITY.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner security-assurance --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Security suites, threat matrix, reports and evidence; no new product UI.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/51-security-assurance`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 51 --package security-assurance --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/51-security-assurance/ENTRY-REVIEW.md`. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Adversarial tests prove all role, tenant, credential, protocol, and network boundaries.
2. No critical secret/data leak or unauthorized state change survives the suite.
3. Every finding is remediated and rerun to a final clear result.

**Test layers**: SAST/dependency scan, DAST, protocol fuzz/adversarial, authorization matrices, secret scans.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD

### Phase 52: Performance and capacity assurance

**Package ID**: `performance-assurance`
**Goal**: Executed load meets PRD acceptance baselines on the real pipeline.
**In scope**: Realistic traffic/data model; HTTP/CMPP submit; routing/Redis/MySQL/queue/connector/receipt/billing paths; latency/throughput/capacity/health-check assertions; bottleneck remediation; correctness under load.
**Out of scope**: HA/failover and release composition.
**Depends on**: Phases 1-50.
**Requirements**: REQ-NFR-PERFORMANCE.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner performance-assurance --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Load suites, profiles, metrics captures and reports; no new product UI.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/52-performance-assurance`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 52 --package performance-assurance --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/52-performance-assurance/ENTRY-REVIEW.md`. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Required product decision**: `DECISIONS.md` records the product owner's confirmed relationship and traffic profile for the PRD's 1000 TPS and 100000-message daily baselines. Until confirmed, neither baseline may be silently dropped or converted into the other; missing decision evidence blocks assurance execution.
**Success Criteria** (observable truths):

1. Executed load meets PRD acceptance baselines on the real pipeline.
2. Idempotency, billing, ordering, and tenant isolation remain correct under load.
3. Bottlenecks and resource limits have reproducible evidence and verified remediation.

**Test layers**: Distributed load, stress/soak, large-data query/export, correctness reconciliations.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD

### Phase 53: Reliability and HA assurance

**Package ID**: `reliability-ha-assurance`
**Goal**: Injected dependency/instance failures preserve durable message and financial invariants.
**In scope**: Stateless multi-instance topology; MySQL/Redis/queue/provider faults; channel failover; worker crash; network partition; gray rollout; rollback; backup/restore; message/ledger reconciliation.
**Out of scope**: Performance sizing, generic observability implementation, and final composition.
**Depends on**: Phases 1-50.
**Requirements**: REQ-NFR-RELIABILITY.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner reliability-ha-assurance --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Fault-injection and recovery/rollback runbooks/evidence; no new product UI.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/53-reliability-ha-assurance`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 53 --package reliability-ha-assurance --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/53-reliability-ha-assurance/ENTRY-REVIEW.md`. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Injected dependency/instance failures preserve durable message and financial invariants.
2. Failover, recovery, and rollback complete under defined evidence without silent loss.
3. Post-recovery reconciliation accounts for every task, receipt, callback, and ledger item.

**Test layers**: Chaos/failure injection, multi-instance split/race, backup/restore and rollback drills.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD

### Phase 54: Observability assurance

**Package ID**: `observability-assurance`
**Goal**: A submission correlates through acceptance, dispatch, receipt, callback, and billing using trace/event IDs.
**In scope**: Assurance of all PRD business events; trace propagation; logs/metrics/traces correlation; service/queue/channel/dependency health feeds; reconciliation against already implemented dashboards/alerts including Phase 44 API status; PII redaction; diagnostic runbooks and injected-failure detection.
**Out of scope**: Any new or modified production UI, business dashboards, and general HA. UI gaps are routed back to the owning production phase and keep TODO open.
**Depends on**: Phases 1-50.
**Requirements**: REQ-NFR-OBSERVABILITY.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner observability-assurance --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Telemetry schemas/pipeline, operational health views already owned by prior UI phases, runbooks/evidence; no new UI.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/54-observability-assurance`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 54 --package observability-assurance --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/54-observability-assurance/ENTRY-REVIEW.md`. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. A submission correlates through acceptance, dispatch, receipt, callback, and billing using trace/event IDs.
2. Every required event/metric is source-consistent, permission-safe, and redacted.
3. Injected failures are detectable and diagnosable through existing operational views and alerts.

**Test layers**: Telemetry schema/contract, trace correlation, metric reconciliation, failure-detection and redaction tests.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD

### Phase 55: Extension conformance assurance

**Package ID**: `extension-conformance-assurance`
**Goal**: Each declared extension can be implemented without modifying unrelated module internals.
**In scope**: Connector, routing condition/action, billing/price, review policy, notification adapter extension contracts; compatibility/versioning; conformance kits; one additional reference implementation per extension class where practical.
**Out of scope**: Shipping new carrier products or unrelated plugin ecosystems.
**Depends on**: Phases 1-50.
**Requirements**: REQ-NFR-EXTENSIBILITY.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner extension-conformance-assurance --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: SPIs, conformance fixtures/suites, compatibility reports; no new UI.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/55-extension-conformance-assurance`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 55 --package extension-conformance-assurance --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/55-extension-conformance-assurance/ENTRY-REVIEW.md`. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Each declared extension can be implemented without modifying unrelated module internals.
2. Conformance kits reject incompatible semantics and prove lifecycle/security/observability hooks.
3. Reference implementations preserve behavior and evidence contracts.

**Test layers**: SPI compatibility, conformance, version migration, fault/security contract tests.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD

### Phase 56: Final cross-protocol release acceptance

**Package ID**: `final-release-acceptance`
**Goal**: Every top-level group and atomic obligation has one owner and executed bidirectional evidence.
**In scope**: Composition only: verify all 108 groups/all 522 atomic obligations; HTTP and CMPP downstream; real HTTP and CMPP upstream; compliance, routing, receipts, Webhook/CMPP reports, billing, lifecycle, UI UAT, archive, remote delivery evidence; rerun the Phase 1 simplified-Chinese copy/export and UTC+8/IANA-timezone contracts against every delivered production surface; release TODO query.
**Out of scope**: New features, new UI, or remediation hidden outside owning phases.
**Depends on**: Phases 1-55.
**Requirements**: Atomic PRD DoD and cross-protocol composition obligations, plus REQ-NFR-COMPATIBILITY product acceptance for OBL-NFR-CHINESE and OBL-NFR-TIMEZONE.
**Owned atomic obligations**: Run `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner final-release-acceptance --assert-unique --assert-traced`; the returned set is authoritative for this phase.
**Primary surfaces**: Cross-protocol E2E suites, evidence index, milestone verification and remote commit record; validates existing UI only and reuses each owning phase's route and `UI-ELEMENTS.md` selectors rather than introducing a final-release selector namespace.
**Entry gate**: Required inputs are dependency `SUMMARY.md`, verification, empty-TODO result, and remote SHA plus this phase's required artifacts under `.planning/phases/56-final-release-acceptance`. Run `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 56 --package final-release-acceptance --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/56-final-release-acceptance/ENTRY-REVIEW.md`. The verification subagent must write `ENTRY-REVIEW.md` with criterion-level PASS/BLOCKER, evidence, and reproducible command/rule. Any missing/uncovered/non-runnable/contradictory/BLOCKER result exits nonzero and prevents implementation. The independent entry reviewer applies the bounded revision cycle from `EXECUTION-STANDARD.md`: at most three review attempts per cycle; a non-decreasing finding count or exhausted cycle escalates with scoped TODOs still open, and only new developer decisions or evidence can start a new cycle.
**Success Criteria** (observable truths):

1. Every top-level group and atomic obligation has one owner and executed bidirectional evidence.
2. Real HTTP/CMPP ingress delivers through real HTTP/CMPP upstream paths with correct compliance, final status, notification, and finance outcomes.
3. Every first-release UI/error/export surface passes the simplified-Chinese contract, and UTC+8 display/storage plus international-message IANA identity pass against real production behavior rather than synthetic foundation fixtures.
4. GSD and final Claude reviews are clear, repository TODO query is empty, and the atomic release commit is visible on the configured GitHub remote.

**Test layers**: Full protocol composition, Playwright UAT, data/finance reconciliation, archive retrieval, security/performance/HA/observability evidence aggregation.
**Exit gate**: Execute obligation-linked tests and evidence. Run the plan checker, GSD goal verification/code review, and Claude review under the bounded revision cycle: each cycle permits at most three review attempts; a non-decreasing BLOCKER/HIGH count or exhausted cycle escalates without completion and leaves scoped TODOs open; new developer decisions or evidence may start a new cycle. Exit only after final GSD and Claude results contain no blocking/HIGH finding and the owned-obligation and TODO queries are empty. Then create one atomic commit, push it to the configured GitHub remote, and record remote/branch/SHA in `SUMMARY.md`.
**Plans**: TBD

## Progress

No schedule or percentage status is maintained. The unchecked TODO set is authoritative.

| Phase set | TODO state | Entry review | Remote commit |
| --- | --- | --- | --- |
| 1-56 | Not yet queried per phase | Not yet executed | Not yet recorded |

## Coverage

- Top-level requirement groups: 108, each with one primary integration phase.
- Atomic obligations: 522 cataloged in `.planning/PRD-OBLIGATIONS.md`, each with exactly one owning package.
- Stable real-upstream obligations: HTTP, CMPP, and final cross-protocol composition are explicit.
- Completion requires zero orphan obligations, zero duplicate owners, zero dependency cycles, final blocking-free reviews, and an empty verified TODO set.
