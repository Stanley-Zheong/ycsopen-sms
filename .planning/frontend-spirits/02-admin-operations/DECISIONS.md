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
