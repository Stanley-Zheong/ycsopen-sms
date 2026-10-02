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
