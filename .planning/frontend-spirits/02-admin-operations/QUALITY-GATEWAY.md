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
| Issue 124 focused React | `npm --prefix web test -- --run test/unit/complaint-case.test.tsx` | Pass | PASS, 1 file / 12 tests, after the first CI feedback fix. |
| Issue 124 Google Chrome | `npm --prefix web run test:e2e -- complaint-case.spec.ts --project=local-google-chrome --reporter=line,json` | Pass | PASS, 11/11 with no skipped, unexpected, or flaky tests, Google Chrome 154, run `37403509270`. |
| Issue 124 backend timeline/state | Focused backend matrix recorded in the Issue 124 verification package | Pass | PASS, 37 tests including service, controller, authorization, exception, migration, and transaction coverage. |
| Issue 124 dependency install | `npm --prefix web ci` | Pass | PASS, 357 packages; the existing 8 dependency advisories remain visible. |
| Issue 124 focused migration/transaction | Focused backend matrix recorded in the Issue 124 verification package | Pass | PASS within the 37-test matrix. |
| Issue 124 authorization | Focused backend matrix recorded in the Issue 124 verification package | Pass | PASS within the 37-test matrix with Spring method security enabled. |
| Issue 124 full frontend | `npm --prefix web test` | Pass | Local run reached 224/225 with one unchanged cross-file-only failure; clean Node 20 CI PASS in run `37402564417`. |
| Issue 124 frontend build | `npm --prefix web run build` | Pass | PASS on the final local diff; the existing bundle-size warning remains. |
| Issue 124 full backend | `mvn -f core/pom.xml test` | Pass | First Java 21 CI ran 1,022 tests; the only failure was the then-open Issue 124 delivery TODO. Final rerun follows the now-empty TODO. |
| Issue 124 diff hygiene | `git diff --check` | Pass | PASS on the final local diff. |
| Issue 124 scoped release boundary | Complaint-management Google Chrome lane; no Compose/runtime/release identity changed | Pass or verified boundary | PASS, fresh/repeated Docker release in run `37403509270`; final planning validator follows the checksum evidence commit. |

## Merge Gate

- Gate item: Each changed operation button has verb, target, effect, and feedback.
- Gate item: Unknown attribution and empty states are represented explicitly.
- Gate item: PR body references the owning issue and evidence.
- Gate item: Independent pre-push review found no remaining BLOCKER, HIGH, or MEDIUM finding after the two reported findings were fixed and re-reviewed.
- Gate item: `Phase 03 real integration` is externally blocked before test execution because `quay.io` rejects the repository's pinned MinIO image; two attempts reproduced the same registry error and clean-owned-resource checks passed.
- Gate item: Issue `#124` change package TODO is empty and its implementation/pre-push reviews contain no BLOCKER/HIGH finding.
