# YCSOpen SMS

## Product intent

YCSOpen SMS is a multi-tenant, multi-channel SMS platform. It connects carrier/provider channels over HTTP, CMPP, SGIP, and SMGP, and exposes HTTP and CMPP access to enterprise tenants. It must combine message acceptance, compliance, routing, delivery, receipts, billing, operational controls, and separate Admin/Tenant portals without creating security or accounting bypasses.

## Core value

An authorized tenant submits a compliant message and receives a truthful final outcome while the platform enforces tenant isolation, data protection, routing policy, real delivery, billing correctness, operational recovery, and regulatory controls.

## Normative sources

- `docs/PRD.md`: product behavior, UI, fields, state machines, data model, flows, and Definition of Done.
- `docs/PRD_REVIEW.md`: ambiguities, implementation risks, and known defects.
- `.planning/REQUIREMENTS.md`: 108 top-level requirement groups and primary integration ownership.
- `.planning/PRD-OBLIGATIONS.md`: 522-item, nine-field atomic completion catalog for numbered sub-behaviors and unnumbered normative obligations.
- `.planning/SCHEMA-OWNERSHIP.md`: unique logical schema/prefix owners, non-overlapping migration namespaces, compatibility/rollback policy, and cross-owner approval law.
- `.planning/EXECUTION-STANDARD.md`: phase execution gates.
- `.planning/PHASE-ARTIFACT-TEMPLATE.md`: required package records.
- `.planning/UI-TEST-CONTRACT.md`: UI inventory, selector, and browser evidence law.
- `.planning/PLAN-REVIEW.md`: blocking findings that this revision resolves.
- `.planning/intel/implementation-audit.md`, `core/docs/ROADMAP.md`, and `web/docs/ROADMAP.md`: implementation reality.

Existing code is reusable only after the relevant atomic obligation is exercised. A class, schema, endpoint, placeholder route, build result, or unrelated passing test is not completion evidence.

## Success definition

### User success

All 108 top-level requirement groups and all 522 atomic PRD obligations have operable behavior, stable traceability, automated verification, and real evidence. The final composition proves secure HTTP and CMPP ingress, real HTTP and CMPP upstream delivery, receipt processing, final state, Webhook/CMPP reports, and correct financial effects.

### Sole completion metric

The verified TODO set for the declared scope is empty. A phase or project is incomplete while any scoped TODO, atomic obligation, required evidence, ENTRY-REVIEW blocker, GSD blocking finding, or Claude BLOCKER/HIGH finding remains unresolved.

## Runtime

- Java 21, Spring Boot 3.3, MySQL 8, and Redis.
- React 18, TypeScript, and Vite.
- Playwright against real application services.
- Git delivery through the configured project remote `https://github.com/Stanley-Zheong/ycsopen-sms.git`.

## Description and trace model

- Every phase has one stable kebab-case package ID.
- Every behavior has one permanent `<package-id>-<number>` ID.
- Every atomic PRD obligation has one permanent obligation ID, a machine-readable `Requirement IDs` field, and exactly one owning package.
- Requirements, obligations, behaviors, UI elements, tests, and evidence are bidirectionally traceable.
- Cross-module bindings cite the exact peer behavior ID rather than depending on narrative implication.
- Top-level requirement ownership identifies the integration owner; the group closes only when all of its atomic obligations are verified, including obligations owned by peer phases.
- Production code uses human-readable domain names and small focused units; comments explain policy/protocol reasoning and cite PRD or behavior IDs where that context is not self-evident.

## Phase package and entry gate

Each phase uses `.planning/phases/<NN>-<package-id>/` and contains `<NN>-SPEC.md`, `<NN>-CONTEXT.md`, at least one `<NN>-*-PLAN.md`, plus `INTENT.md`, `DESIGN.md`, `ITERATIONS.md`, `DECISIONS.md`, `TODO.md`, `TEST-MATRIX.md`, `ENTRY-REVIEW.md`, `CLAUDE-REVIEW.md`, and `EVIDENCE/`; UI phases also contain `<NN>-UI-SPEC.md`, `UI-ELEMENTS.md`, Pencil `.pen` artifacts, clickable HTML prototype output, and checksum-bound `EVIDENCE/ui-contract.json`. A phase declaring persistence migrations also contains `SCHEMA-CLAIMS.md`.

For Phase 1, the bootstrap entry command in `ROADMAP.md` uses the already-present standard-library Ruby tool `.planning/tools/bootstrap-phase-01.rb`; it does not depend on validators that Phase 1 is responsible for creating. It derives the exact owned-obligation set from the nine-field catalog and checks each obligation ID independently in SPEC, TODO, and TEST-MATRIX, the complete artifact set, at least one plan, and every task's files/action/verify/done contract.
For every later phase, `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb` must verify:

- dependency closure, dependency empty-TODO evidence, and remote commit identities;
- presence and structure of every required phase artifact;
- bidirectional requirement/obligation/behavior/test/evidence trace;
- plan task structure with nonempty files/action/verify/done declarations;
- UI design-stage manifest/prototype/test-ID/Playwright alignment when applicable; React and real-browser execution are exit evidence, not implementation-entry prerequisites;
- schema prefix ownership, migration namespace uniqueness, dependency compatibility, expand-migrate-contract step, rollback, and cross-owner decision approval when migrations are declared;
- the authoritative scoped TODO query.

The repository already contains the standard-library Ruby entry and UI validators plus their fixture self-test. A validator self-test PASS proves the validator contract only; it never proves a real phase package ready. The real Phase 2 directory is absent and its exact gate currently fails closed.

An independent verification subagent reads all owned obligations and artifacts and writes `ENTRY-REVIEW.md`. Every criterion has exactly one `PASS` or `BLOCKER`, evidence path, and reproducible command or inspection rule. The entry validator runs as preflight, runs again after review/correction with the completed review file, and authorizes implementation only on the final zero-exit result. Missing inputs, uncovered obligations, non-runnable checks, contradictions, or any `BLOCKER` fail closed.

All entry reviewers, plan checkers, GSD verifiers/code reviewers, and Claude reviewers use the same bounded revision cycle. A cycle permits no more than three review attempts. If the unresolved BLOCKER/HIGH count does not decrease between attempts, or the third attempt remains blocked, the cycle escalates and every affected TODO remains open. Escalation never authorizes implementation or completion. A new cycle starts only after a new developer decision or new executable evidence; eventual completion still requires a final result with no blocking/HIGH finding.

## UI source hierarchy

- Pencil `.pen` is the visual source.
- The generated HTML prototype is the clickable interaction source.
- React is the production source.
- Stable page and state IDs map all three sources.

Phase 1 owns machine validation across the page registry, UI manifest, React routes, real DOM test IDs, and Playwright selectors. A production deviation updates `UI-ELEMENTS.md`, `DECISIONS.md`, and `TEST-MATRIX.md`, then reruns UI reconciliation. Dynamic rows use a stable semantic row test ID and a separate non-sensitive business key.

UI phases use two explicit lifecycle gates. `validate-phase-entry.rb --ui` invokes `validate-ui-contract.rb --stage design`, which checks UI-SPEC, strict UI-ELEMENTS/TEST-MATRIX trace, Pencil, HTML, manifest, and prototype interaction tests without requiring unimplemented React. Before a production UI phase can empty TODO or commit, `--stage production` reruns design and additionally requires `web/src` React route/DOM evidence, per-obligation real Playwright blocks, and checksum-bound executed PASS metadata/report. Phase 2 remains a prototype-only design foundation and reruns design at exit; production stage rejects it and it cannot close later React obligations.

Schema changes use `.planning/SCHEMA-OWNERSHIP.md`. `DESIGN.md` states exactly `Schema migrations: none` or `Schema migrations: declared`; a declared phase claims concrete objects and migration IDs under its owner's registered prefix/namespace in `SCHEMA-CLAIMS.md`. Cross-owner changes retain the registered owner and require a cited `DR-*` approval in `DECISIONS.md`.

The UI foundation phase owns only design-foundation work: complete Admin/Tenant IA and prototypes, role matrix, brand snapshot, design tokens, prototype shells, shared component/state specifications, global notification destination, Pencil baseline, clickable HTML baseline, target UI manifest schema, and test-ID registry. It uses the local Pencil MCP and `/Users/laosanzheong/Documents/codebases/hengshi-jarvis/projectlogs/907/uiskill`; it does not implement React production UI, and production surfaces remain owned by focused later phases.

The ycsan visual reference is reproducibly pinned as follows:

- Local checkout: `/Users/laosanzheong/Documents/codebases/ycsan/ycsan-aisms-web`.
- Git remote: `https://github.com/Stanley-Zheong/ycsan.git`.
- Full commit SHA: `f4f8aae9c05a5b527aafd725b1d7410a3b3ad31b`.
- CSS source: `app/globals.css`.
- Screenshot sources: `public/screenshots/homepage.png` and `public/screenshots/products.png`.
- Verified CSS SHA-256: `6931f74f3bbe90f76b972c907b9b519bc93a348fe3e74ba20f7eacfb3ce1fc53`.
- Verified homepage screenshot SHA-256: `640074fbfb8015e2e5e6a1cf1ba62f5af4e2064abef130e75165fc00f1fd2258`.
- Verified products screenshot SHA-256: `080e19fecbffbcc3c84ebb5a55bbffd072201e03d2fe7fd1a1a247ea8a2423a3`.

These hashes were verified from the pinned checkout. Phase 2 reruns the checksum command and records the adopted CSS snapshot and both screenshot files in its DESIGN, DECISIONS, TEST-MATRIX, and evidence index. A later checkout or visual-source change requires a new recorded commit SHA and checksums; an abbreviated SHA or unverified working tree is not provenance evidence.

## Fixed exit and delivery gate

Each phase:

1. Runs GSD goal verification and code review under the bounded revision cycle.
2. Runs Claude as an independent read-only reviewer of diff, contracts, tests, and evidence under the same bounded cycle.
3. Fixes Claude BLOCKER/HIGH findings and reruns Claude within the active cycle. A stalled or exhausted cycle escalates without completion and leaves TODO open; after a new decision or evidence, a new cycle may begin. `CLAUDE-REVIEW.md` must ultimately end with a final result containing no BLOCKER/HIGH.
4. For a production UI phase, runs its exact `validate-ui-contract.rb --stage production` command and requires PASS; Phase 2 reruns its scoped design gate.
5. Runs the scoped atomic-obligation and TODO queries; any open item fails the gate.
6. Creates one atomic commit containing the module's specification, design, decisions, code, tests, and evidence references.
7. Pushes the commit to the configured GitHub remote and records remote, branch, and remote SHA in `SUMMARY.md`.
8. Advances `.planning/STATE.md` only after the remote commit is visible.

## Durable product decisions

- Registration and platform-originated operational notifications use a focused platform system-message capability with controlled templates, recursion guard, provider boundary, delivery evidence, and audit. They never depend on tenant-owned resources.
- The early system-message capability delivers through a direct platform-bootstrap HTTP provider adapter using environment/KMS-protected platform credentials and authoritative provider sandbox evidence. It does not depend on channel configuration, routing, tenant resources, or billing; later delivery replaces only the SPI adapter.
- Runtime content review scans the final rendered message including variable values.
- Trial sending consumes trial quota and records an explicit trial ledger classification rather than silently using prepaid charging.
- Channel health may evict routing candidates before durable dispatch exists, but in-flight migration is owned by a later task-migration phase.
- Provider status taxonomy precedes connectors, retry, billing finality, details, and analytics.
- Generic outbound callback transport is separate from uplink normalization and unsubscribe policy.
- Bulk, console, scheduled, and downstream CMPP ingress reuse the proven single-message acceptance pipeline.
- Phase 23 owns API acceptance/idempotency without UI. Phase 26 owns the Tenant console's production timeout/retry state and proves retries reuse Phase 23 without duplicate task or charge.
- Phase 34 owns aggregate computation without production UI. Phase 44 owns the channel, tenant, and signature/template resource-statistics pages that consume and reconcile those aggregates.
- Statistics, complaint ratios, finance analytics, and alerts use real message, receipt, complaint, and ledger sources and expose unknown or incomplete data.
- Tenant termination has a machine-readable participant inventory and cannot complete until active work, sessions, callbacks, credentials, resources, settlement, and retention obligations are resolved.
- Phase 7 owns the production system-configuration page/API; Phase 15 owns unified production resource-review history; Phase 50 owns the production Tenant help/developer center.
- Phase 44 owns the production Admin API-status monitor. Phase 54 only assures its telemetry, correlation, and failure diagnosis and may not add UI.
- Phase 5 owns the production shared-console safe internal-error UI. Phase 54 owns injected-failure trace, alert, and full-chain log correlation and may not add that UI.

## PLAN-REVIEW resolution index

| Finding | Structural resolution |
| --- | --- |
| PR-001 | Every roadmap phase has an exact artifact path, entry command, criterion-level `ENTRY-REVIEW.md`, evidence/command requirement, fail-closed result, and bounded revise/check/escalate cycle that never treats escalation as completion. |
| PR-002 | `.planning/PRD-OBLIGATIONS.md` catalogs numbered sub-behaviors and unnumbered IA, field, state, exception, flow, DoD, data, permission, and display obligations with nine machine-readable fields, one owner, requirement IDs, and planned trace. |
| PR-003 | Phase 2 is `console-design-system-prototype-foundation`, preceding business UI. |
| PR-004 | Phase 1 owns UI manifest/route/DOM/test-ID/Playwright bidirectional drift checks and dynamic-row selector validation. |
| PR-005 | Phase 3 delivers cryptographic storage before Phase 5 identity; password hashing remains in Phase 5; Phase 6 combines current RBAC, masked/reveal access, audit, and detection. |
| PR-006 | Phase 4 delivers controlled platform system messages with provider evidence, recursion guard, and audit; onboarding and alerting depend on it. |
| PR-007 | Phase 11 owns health/pools/candidate eviction; Phase 25 owns in-flight task migration after durable outbox/dispatcher delivery. |
| PR-008 | Phase 20 status taxonomy precedes Phase 21 routing and all delivery, receipt, retry, billing, detail, and analytics consumers. |
| PR-009 | Phase 28 owns generic Webhook transport, Phase 32 owns uplink normalization/search/push, and Phase 33 owns unsubscribe suppression/evidence/statistics. |
| PR-010 | Finance analytics/warnings, statistics/reports, export/retention, and security/performance/reliability/observability/extension/final acceptance are separate focused phases. |
| PR-011 | Top-level groups no longer close coarse sub-behaviors; atomic obligations split overview, query/detail/action/export, and other cross-phase behavior under their actual owner. |
| PR-012 | Phase 49 termination depends on batch/tasks, callbacks, protocol sessions, uplink/unsubscribe, settlement, credentials/resources, and retention, and owns a machine-readable participant inventory. |
| PR-013 | Atomic obligations explicitly identify real HTTP upstream delivery, real CMPP upstream delivery, and final cross-protocol composition. |
| PR-014 | All 522 atomic records use the nine-field schema; `Requirement IDs` accepts known top-level IDs and registered `PROJECT-*` IDs only. |
| PR-015 | The standard-library Ruby catalog validator performs executable count, uniqueness, owner, link, coverage, and selector checks. |
| PR-016 | The already-present Phase 1 Ruby bootstrap derives its exact owned set and validates artifact, per-ID trace, plan, task, and entry-review structure without future tooling. |
| PR-017 | Entry, UI, plan, GSD, code, and Claude review use the same bounded cycle; escalation is never completion and leaves TODO open. |
| PR-018 | Phase 2 has four independently queryable plans across token/shell, Admin IA, Tenant IA, and integration-QA lanes. |
| PR-019 | Phase 2 owns prototypes only; Phases 7, 15, 44, and 50 own the identified production surfaces, while Phase 54 only assures existing UI. |
| PR-020 | All atomic `element:` references use the complete stable `data-testid` form and are validator-enforced. |
| PR-021 | UI acceptance derives from every owned atomic obligation and every machine-linked top-level/project integration ID. |
| PR-022 | The ycsan checkout, remote, full SHA, source files, and verified SHA-256 values are pinned and must be re-recorded by Phase 2. |
| PR-023 | P2-P56 entry and UI commands resolve to repository-present standard-library Ruby validators with positive and fail-closed fixture coverage; real phase gates remain unapproved until their artifacts pass. |
| PR-024 | `SCHEMA-OWNERSHIP.md` registers all 56 package domains, unique prefixes and migration namespaces; phase entry rejects ownership, namespace, dependency, compatibility, rollback, cross-owner approval, and duplicate migration conflicts. |

## Product exclusions

- Voice messaging and MMS.
- A complete international-message local compliance system.
- Online customer-service software beyond the specified entry and complaint workflow.
- AI content generation and marketing automation.
- Full internationalization and user-defined drag-and-drop dashboard layout.
- Self-service password recovery/reset beyond the PRD. Locked accounts use the specified audited administrator manual-unlock path; adding recovery later requires a new threat-modelled requirement and phase.
