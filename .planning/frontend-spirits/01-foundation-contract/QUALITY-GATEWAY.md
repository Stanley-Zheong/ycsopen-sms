# Spirit 01 Quality Gateway

## Required Commands

| Gate | Command | Required result | Evidence |
|---|---|---|---|
| Diff hygiene | `git diff --check` | Pass | Not recorded |
| Frontend install | `npm --prefix web ci` | Pass when dependencies change or CI cache is untrusted | Not recorded |
| Unit tests | `npm --prefix web test` | Pass | Not recorded |
| Build | `npm --prefix web run build` | Pass | Not recorded |
| Chrome Playwright | Spirit-specific Chrome command covering QueryPanel, form, table, and action confirmation | Pass | Not recorded |

## Merge Gate

- Gate item: Scoped TODO set is empty with evidence.
- Gate item: Shared component behavior is covered by unit tests.
- Gate item: Representative Chrome Playwright route evidence is recorded.
- Gate item: PR body lists changed routes, changed selectors, verification commands, and boundaries.
