# Spirit 02 Quality Gateway

## Required Commands

| Gate | Command | Required result | Evidence |
|---|---|---|---|
| Dependency install | `npm --prefix web ci` | Pass | PASS on 2026-10-02; existing audit warnings retained |
| Diff hygiene | `git diff --check` | Pass | PASS on 2026-10-02 |
| Targeted unit tests | `npm --prefix web test -- --run test/unit/complaint-case.test.tsx` | Pass | PASS, 1 file / 10 tests |
| Frontend suite | `npm --prefix web test` | Pass | Local shared-run attempt had 8 unrelated timeout/async-route failures while the changed complaint file passed; clean PR CI is the required final result |
| Build | `npm --prefix web run build` | Pass | PASS; existing bundle-size warning retained |
| Chrome Playwright | `npm --prefix web run test:e2e -- complaint-case.spec.ts --project=local-google-chrome --reporter=line,json` | Pass | Local host has no Google Chrome; PR Docker-release job now runs this exact 5-case suite with `/usr/bin/google-chrome` |
| Backend contract | `mvn -f core/pom.xml -Dtest=ComplaintCaseServiceTest,ComplaintCaseControllerTest test` | Pass | PASS, 12 tests, using the pre-populated public Maven cache in offline mode |

## Merge Gate

- Gate item: Each changed operation button has verb, target, effect, and feedback.
- Gate item: Unknown attribution and empty states are represented explicitly.
- Gate item: PR body references the owning issue and evidence.
- Gate item: Independent pre-push review found no remaining BLOCKER, HIGH, or MEDIUM finding after the two reported findings were fixed and re-reviewed.
