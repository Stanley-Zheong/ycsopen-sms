# Routing Governance, Cockpit, and Simulator Development Plan

> Plan-mode artifact for `docs/PRD_ROUTING_CIRCUIT_RETRY_V2.md`.
> Each phase maps to exactly one GitHub issue, one implementation branch, one PR, and one merge boundary.

## Source Documents

- Product PRD: `docs/PRD_ROUTING_CIRCUIT_RETRY_V2.md`
- Cross-system PRD: `docs/PRD_V2.md`
- Frontend convention: `docs/frontend页面实现规范.md`
- Repository contract: `AGENTS.md`

## External Review Result

Claude Code CLI was used as a read-only product and architecture reviewer.

- First review result: not approved; blockers covered rollback ambiguity, rule conflicts, weight-group ambiguity, customer-success simulation permissions, and simulator safety acceptance.
- PRD was revised to resolve the blockers.
- Second review result: approved to enter development planning, with one local warning about a rule-table weight column.
- Final PRD update removed that local ambiguity by defining weighted routing as a channel-pool member property and rule-table weight as a read-only derived summary.

## Global Acceptance Rules

- No phase is complete until its scoped TODO set is empty and executable verification evidence is recorded in the PR.
- Every phase must include issue reference, verification commands, and known limitations in the PR body.
- Backend behavior changes require `mvn -f core/pom.xml test`.
- Frontend behavior changes require `npm --prefix web ci`, `npm --prefix web test`, and `npm --prefix web run build`.
- Each phase must preserve historical auditability: no physical deletion of historically referenced policy, rule, circuit, retry, or decision records.
- Frontend changes must follow `docs/frontend页面实现规范.md` and show source, freshness, empty state, failure feedback, and action consequences.
- Implementation commits must not go directly to `main`; each phase lands through its issue branch and PR.

## Phase 0: PRD and Architecture Acceptance

- Issue: #101 `Phase 0: PRD and architecture acceptance for routing governance V2`
- Branch scope: documentation and planning only.
- Primary deliverables: V2 PRD, Claude review evidence, phase-to-issue development plan.
- Acceptance criteria:
  - The PRD resolves routing source, old `route_rules` boundary, rollback semantics, rule conflict handling, circuit policy, retry taxonomy, RBAC, homepage cockpit, and simulator lifecycle.
  - Claude Code CLI review has no blocker for entering development planning.
  - Issues #101 through #106 exist and map one-to-one with phases.
- Verification:
  - `git diff --check`
  - `rg -n "Open item|^- \\[ \\]|目标版本重新|建议唯一|target_type.*WEIGHT" docs/PRD_ROUTING_CIRCUIT_RETRY_V2.md`

## Phase 1: Production Versioned Routing Policy Loop

- Issue: #102 `Phase 1: Production versioned routing policy loop`
- Branch pattern: `phase-1-versioned-routing-policy`
- Primary deliverables: versioned policy data model, CSV import validation, publish/supersede flow, real-send lookup, decision snapshot.
- Acceptance criteria:
  - New message submissions read scoped `ACTIVE` versioned routing policy.
  - CSV import creates deterministic rules with unique priority and conflict validation.
  - Publishing supersedes the previous scoped active version.
  - Every real send records version, rule, target, skipped targets, circuit state, fallback reason, and compatibility source if any.
- Verification:
  - Backend tests for import, conflict rejection, scoped lookup, fallback, publish, supersede, and snapshot persistence.
  - Frontend tests for active version, rule count, validation errors, and decision explanation.
  - Required repository checks for touched backend and frontend areas.

## Phase 2: Circuit Breaker and Retry Governance

- Issue: #103 `Phase 2: Circuit breaker and retry governance`
- Branch pattern: `phase-2-circuit-retry-governance`
- Primary deliverables: circuit policy object, circuit event/state processing, raw-code mapping, normalized retry policy, retry snapshot, emergency cancel/replan.
- Acceptance criteria:
  - Circuit thresholds are not stored in route rules and are governed by scoped circuit policy.
  - Circuit events from real sends, health checks, complaint linkage, manual operation, and mock simulation update current state with source attribution.
  - Raw upstream error codes map to normalized categories before retry policy evaluation.
  - Existing retry tasks keep their generated snapshot unless explicitly canceled or replanned with audit evidence.
- Verification:
  - Backend tests for circuit transitions, source attribution, code mapping, retry snapshots, and emergency replan audit.
  - Frontend tests for circuit table, retry table, write permissions, and explanatory empty/error states.

## Phase 3: Full Policy Lifecycle and Audit UX

- Issue: #104 `Phase 3: Full policy lifecycle and audit UX`
- Branch pattern: `phase-3-policy-lifecycle-audit`
- Primary deliverables: draft, validation, scheduled activation, rollback-as-new-version, archive, historical audit views, customer-success read-only simulation permissions.
- Acceptance criteria:
  - Policy versions support full lifecycle states without mutating historical versions back to active.
  - Rollback creates a new active version after validation and records source version, target historical version, operator, approver, and reason.
  - Archived and historically referenced records are read-only and remain explainable.
  - Customer-success users can run read-only simulation and view decision audit, but cannot publish, rollback, modify circuit state, or change retry policy.
- Verification:
  - Backend state-machine tests for valid and invalid transitions.
  - Frontend tests for lifecycle actions, rollback explanation, archive read-only behavior, and permission boundaries.

## Phase 4: Home Cockpit Dashboard

- Issue: #105 `Phase 4: Home cockpit dashboard`
- Branch pattern: `phase-4-home-cockpit-dashboard`
- Primary deliverables: charted cockpit homepage, dashboard aggregation APIs if needed, simulated-traffic filter, drill-down links, simulator capability control visibility.
- Acceptance criteria:
  - Home dashboard displays send volume, success rate, failure rate, active tenants, channel health, tenant ranking, route-hit distribution, retry queue, financial risk, complaint risk, and alert events.
  - Each metric shows source, scope, freshness, and simulated-traffic inclusion.
  - Cards and charts drill down to relevant detail pages.
  - Simulator controls are visible only when backend runtime capability `simulationEnabled` is true and the user has permission.
- Verification:
  - Frontend tests for charts, source/freshness labels, empty states, simulated-traffic filter, drill-downs, and control visibility.
  - Backend tests for dashboard aggregation endpoints if backend behavior changes.

## Phase 5: Test Traffic Simulator and Mock Carrier

- Issue: #106 `Phase 5: Test traffic simulator and mock carrier`
- Branch pattern: `phase-5-test-traffic-simulator`
- Primary deliverables: seeded test tenants/templates/API keys, simulator batch lifecycle, controlled worker, real API submissions, mock carrier outcomes, traffic tagging, Docker verification.
- Acceptance criteria:
  - Test environment seeds 5 test tenants, each with 4 approved templates and usable API credentials.
  - Simulator starts and stops from the homepage and calls the real SMS submission API through a controlled worker.
  - Generated content satisfies approved template variables and uses safe test numbers.
  - Mock carrier produces configurable success, failure, timeout, unknown, and channel-error outcomes.
  - Simulated traffic is tagged and filterable in dashboard, detail, statistics, finance, and audit views.
  - Hard limits, idempotent `submitId`, entrance rejection isolation, quota exhaustion behavior, and worker failure isolation are verified.
- Verification:
  - Backend tests for seed data, worker lifecycle, idempotency, hard limits, entrance rejection, mock result handling, and tagging.
  - Frontend tests for start/stop controls, batch status, counters, filters, and failure states.
  - Docker test-environment verification shows simulated traffic in the cockpit without real carrier calls.

## Merge Discipline

- Start each phase from updated `main` after the previous phase PR is merged.
- Keep each phase PR scoped to its issue; defer out-of-scope findings into new issues rather than widening the phase.
- Merge only after required checks pass and PR evidence proves the phase acceptance criteria.
- Deploy the test Docker environment only from merged `main` unless a phase issue explicitly requires branch preview validation.
