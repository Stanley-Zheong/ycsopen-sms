# Spirit 03 Quality Gateway

## Required Commands

| Gate | Command | Required result | Evidence |
|---|---|---|---|
| Diff hygiene | `git diff --check` | Pass | Not recorded |
| Unit tests | `npm --prefix web test` plus targeted finance tests | Pass | Not recorded |
| Build | `npm --prefix web run build` | Pass | Not recorded |
| Chrome Playwright | Finance/tenant commercial route coverage for selected issue | Pass | Not recorded |
| Backend checks | `mvn -f core/pom.xml test` when API, billing, or persistence behavior changes | Pass or not applicable reason | Not recorded |

## Merge Gate

- Gate item: Trial, prepaid, postpaid, billing, and invoice terms are not conflated.
- Gate item: Finance actions have target-aware confirmation and audit input when required.
- Gate item: PR body records verification evidence and unresolved product decisions.
