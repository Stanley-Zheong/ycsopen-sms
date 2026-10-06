# Spirit 02 Quality Gateway

## Required Commands

| Gate | Command | Required result | Evidence |
|---|---|---|---|
| Dependency install | `npm --prefix web ci` | Pass | PASS on 2026-10-02; existing audit warnings retained |
| Diff hygiene | `git diff --check` | Pass | PASS on 2026-10-02 |
| Targeted unit tests | `npm --prefix web test -- --run test/unit/complaint-case.test.tsx` | Pass | PASS, 1 file / 10 tests |
| Frontend suite | `npm --prefix web test` | Pass | PASS in PR run `36967118021` (`Web / Node 20`) |
| Build | `npm --prefix web run build` | Pass | PASS; existing bundle-size warning retained |
| Chrome Playwright | `npm --prefix web run test:e2e -- complaint-case.spec.ts --project=local-google-chrome --reporter=line,json` | Pass | PASS, 5/5, in PR run `36967118021` with `/usr/bin/google-chrome`; raw JSON artifact preserved and checked non-empty |
| Backend contract | `mvn -f core/pom.xml -Dtest=ComplaintCaseServiceTest,ComplaintCaseControllerTest test` | Pass | PASS, 12 tests, using the pre-populated public Maven cache in offline mode |
| Issue 124 focused React | `npm --prefix web test -- --run test/unit/complaint-case.test.tsx` | Pass | PASS, 1 file / 11 tests, on the final local diff. |
| Issue 124 Google Chrome | `npm --prefix web exec -- playwright test complaint-case.spec.ts --config web/playwright.config.ts --project=local-google-chrome --workers=1` | Pass | Local discovery PASS, 11 tests. Execution is assigned to PR CI because the local image has no `/usr/bin/google-chrome`. |
| Issue 124 backend timeline/state | Focused backend matrix recorded in the Issue 124 verification package | Pass | PASS, 37 tests including service, controller, authorization, exception, migration, and transaction coverage. |
| Issue 124 dependency install | `npm --prefix web ci` | Pass | PASS, 357 packages; the existing 8 dependency advisories remain visible. |
| Issue 124 focused migration/transaction | Focused backend matrix recorded in the Issue 124 verification package | Pass | PASS within the 37-test matrix. |
| Issue 124 authorization | Focused backend matrix recorded in the Issue 124 verification package | Pass | PASS within the 37-test matrix with Spring method security enabled. |
| Issue 124 full frontend | `npm --prefix web test` | Pass | Local run reached 224/225; one unchanged Phase 5 test failed only in the cross-file run and passed 8/8 in isolation. Clean Node 20 CI is the authoritative full-suite gate. |
| Issue 124 frontend build | `npm --prefix web run build` | Pass | PASS on the final local diff; the existing bundle-size warning remains. |
| Issue 124 full backend | `mvn -f core/pom.xml test` | Pass | Local run reached 1,001 tests and exposed existing environment/repository gates outside the changed behavior; clean Java 21 CI is authoritative. |
| Issue 124 diff hygiene | `git diff --check` | Pass | PASS on the final local diff. |
| Issue 124 scoped release boundary | Complaint-management Google Chrome lane; no Compose/runtime/release identity changed | Pass or verified boundary | Final branded-Chrome execution and planning-validator evidence are assigned to PR CI. |

## Merge Gate

- Gate item: Each changed operation button has verb, target, effect, and feedback.
- Gate item: Unknown attribution and empty states are represented explicitly.
- Gate item: PR body references the owning issue and evidence.
- Gate item: Independent pre-push review found no remaining BLOCKER, HIGH, or MEDIUM finding after the two reported findings were fixed and re-reviewed.
- Gate item: `Phase 03 real integration` is externally blocked before test execution because `quay.io` rejects the repository's pinned MinIO image; two attempts reproduced the same registry error and clean-owned-resource checks passed.
- Gate item: Issue `#124` change package TODO is empty and its implementation/pre-push reviews contain no BLOCKER/HIGH finding.
