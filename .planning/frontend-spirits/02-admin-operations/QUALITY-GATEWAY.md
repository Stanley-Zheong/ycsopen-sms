# Spirit 02 Quality Gateway

## Required Commands

| Gate | Command | Required result | Evidence |
|---|---|---|---|
| Diff hygiene | `git diff --check` | Pass | Not recorded |
| Unit tests | `npm --prefix web test` plus targeted operation page tests | Pass | Not recorded |
| Build | `npm --prefix web run build` | Pass | Not recorded |
| Chrome Playwright | Admin operations route coverage for selected issue | Pass | Not recorded |
| Backend contract | API test or documented existing-contract boundary when action persistence is involved | Pass or boundary recorded | Not recorded |

## Merge Gate

- Gate item: Each changed operation button has verb, target, effect, and feedback.
- Gate item: Unknown attribution and empty states are represented explicitly.
- Gate item: PR body references the owning issue and evidence.
