# Spirit 04 Quality Gateway

## Required Commands

| Gate | Command | Required result | Evidence |
|---|---|---|---|
| Diff hygiene | `git diff --check` | Pass | Not recorded |
| Unit tests | `npm --prefix web test` plus targeted workbench tests | Pass | Not recorded |
| Build | `npm --prefix web run build` | Pass | Not recorded |
| Chrome Playwright | Workbench route coverage for selected issue | Pass | Not recorded |
| Backend checks | `mvn -f core/pom.xml test` when command API behavior changes | Pass or not applicable reason | Not recorded |

## Merge Gate

- [ ] Commands disclose target and snapshot.
- [ ] Bulk actions cannot submit partial or incomplete targets.
- [ ] Exports disclose included datasets and excluded filters.
