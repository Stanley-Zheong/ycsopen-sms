# Spirit 02 Decisions

## DR-FE02-001: Operations Actions Must Be Verb-First

### Status
Accepted

### Context
Issue `#92` reports that alert-history buttons are impossible to understand from the page. Prior issue `#91` showed reason inputs detached from concrete operations.

### Decision
Operations buttons must use explicit verb-object labels and open a contextual confirmation when state changes. Generic labels are allowed only when accompanied by an accessible description that names the exact effect.

### Consequences

- Some existing button copy must change before behavior can be accepted.
- Tests assert observable action intent, not just selector presence.

## DR-FE02-002: Recovery Follows Persisted Remediation State

### Status
Accepted

### Context

Complaint recovery is valid only for a failed remediation. A page-local record
identifier is insufficient because it is lost after refresh and does not prove
that the remediation failed.

### Decision

The complaint page reads persisted remediation records and enables recovery
only for the latest `FAILED` record of the selected complaint. `APPLIED` and
`RECOVERED` records remain visible but cannot be submitted to recovery.

### Consequences

- The read API exposes remediation status and failure evidence.
- Recovery eligibility survives page refresh.
- Unit and Chrome tests distinguish successful remediation from failed
  remediation.

## DR-FE02-003: Complaint Trend Uses Stored Case Creation Date

### Status
Accepted

### Decision

The complaint analytics trend groups stored complaint cases by calendar date
of `created_at`. The API returns an ordered daily series; the page renders an
explicit empty state when no cases exist.

## DR-FE02-004: Alert History Scope Tabs Expose One Selected State

### Status
Accepted

### Context
Issue `#92`: the `全部 / 活跃 / 严重` alert-history buttons filter the list on click but render identically, so users cannot tell which scope is active or what a click will change. PRD F-11.7 and Phase 35 `UI-ELEMENTS.md` both define them as an "Alert tabs" control that must show `tab state`.

### Decision
The three scope buttons become an ARIA tablist: `role=tablist` on the container, `role=tab` + `aria-selected` + a bold/underline selected style on each button, a hint line describing what each scope filters (`全部=完整告警历史；活跃=仍处于 ACTIVE 状态的告警；严重=级别为 HIGH 或 CRITICAL 的告警`), and a stable `data-testid="admin-alert-engine-scope-tab-<scope>"` per tab. Clicking a tab applies the existing client-side filter immediately. No API contract changes.

### Consequences
- Playwright asserts initial selection, selection change, row-set narrowing per scope, and `aria-selected` state.
- The duplicate "历史确认 / 历史静音" buttons stay but are out of scope for this decision; DR-FE02-001 verb-first labels still apply to row actions.
