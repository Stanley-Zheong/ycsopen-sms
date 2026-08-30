---
name: feature-delivery-guardrails
description: Use for non-trivial ycsopen-sms features that cross API, service, persistence, asynchronous SMS processing, billing, receipts, tenant permissions, web UI, or documentation boundaries. Produces ownership, state-flow, compatibility, and regression contracts before implementation.
---

# Feature delivery guardrails

Load after `skills/SKILL.md` when a phase crosses more than one runtime or data
boundary.

Before substantial coding, record in the active phase DESIGN/DECISIONS:

1. Scope table: persisted intent, derived state, public contract, compatibility
   behavior, and explicit exclusions.
2. Ownership matrix: controller, service, repository, migration, scheduler,
   protocol adapter, React page/store, and documentation owner for each rule.
3. End-to-end state map: request -> validation -> enqueue -> routing -> channel
   submission -> receipt/retry -> billing/complaint/audit -> UI/API readback.
4. Validation ladder: pure unit, service, persistence/migration, API, browser,
   protocol/external-boundary, and operational evidence actually needed.
5. Regression matrix: roles, tenants, task states, channel outcomes, billing
   modes, boundaries, failure/retry/recovery, and unsupported branches.

Rules:

- Every semantic rule has one owner; callers may adapt but must not redefine it.
- Ambiguous tenant, account, route, channel, price, or message identity fails
  explicitly; never choose the first matching record as a hidden fallback.
- Compatibility behavior names the historical population and removal condition.
- Async success means the required terminal/downstream result is observed, not
  merely that enqueue or HTTP acceptance succeeded.
- A behavior projected into API, dashboard, task detail, receipt, billing, and
  export views must remain consistent or expose an explicit freshness/state
  distinction.
- If implementation reveals a wrong owner, stop feature expansion, update the
  phase decision, refactor to one owner, and remove obsolete duplicate logic.

Completion requires the selected regression matrix, docs/runtime parity,
duplicate-owner audit, blocking-free reviews, and an empty phase TODO set.
