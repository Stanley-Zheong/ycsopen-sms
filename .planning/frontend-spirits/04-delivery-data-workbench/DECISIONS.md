# Spirit 04 Decisions

## DR-FE04-001: Workbench Actions Are Snapshot Commands

### Status
Accepted

### Context
Message operations and exports can operate on filters, loaded targets, grouped errors, and backend limits. Prior issue `#91` showed risks around detached inputs and ambiguous bulk action targets.

### Decision
Workbench commands must disclose and lock the target snapshot before submission. Unsupported filters are disclosed and omitted instead of silently sent.

### Consequences

- Export and bulk action dialogs may need additional explanatory copy.
- Tests must assert request payload and target snapshot, not only button clicks.
