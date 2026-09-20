# Spirit 01 Decisions

## DR-FE01-001: Shared Components Are Product Contract

### Status
Accepted

### Context
Repeated issues around control height, query layout, button alignment, empty states, and detached reason inputs show that UI structure cannot be left to page-local implementation.

### Decision
Treat shell, QueryPanel, form, table, modal, action confirmation, and `data-testid` naming as product contract. Page-specific work must reuse or extend shared components instead of cloning local variants.

### Consequences

- Later spirits can focus on business behavior instead of re-solving layout.
- Shared component changes require representative cross-page regression tests.

### References

- `docs/frontend页面实现规范.md`
- `docs/ISSUE_BUG_RETROSPECTIVE.md`
- GitHub issues `#52`, `#58`, `#87`, `#88`, `#89`, `#91`
