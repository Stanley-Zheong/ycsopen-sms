# Issue 87 Verification

| Gate | Result |
|---|---|
| `npm --prefix web ci` | PASS — 357 packages installed; npm reported 7 audit findings |
| Targeted QueryPanel/AppShell unit tests | PASS — 16/16 after an observed 3-test red phase |
| Full unit suite | PASS — 50 files / 186 tests in 258.19s |
| Production build | PASS — 306 modules; CSS 59.15 kB, JavaScript 814.06 kB; existing chunk-size warning only |
| Issue Chrome contract | PASS — one `pw-issue-87-admin-form-contract` test covering all four behavior IDs, 56 routes and the exact 45-route visible-form set; 20.00s |
| Affected Chrome regression suite | PASS — 13/13 isolated cases: 7 QueryPanel, 3 control sizing, and 3 issue #108 shared-shell cases |
| Scoped ESLint | PASS — changed TypeScript/TSX files produced no findings |
| `git diff --check` | PASS — final documented tree |
| Backend `mvn -f core/pom.xml test` | ENVIRONMENT BOUNDARY — 986 tests, 7 failures, 4 errors, 33 skipped; failures are confined to absent Ruby process harnesses, process-reaping assumptions, and unavailable migration configuration; no backend file changed |
| Backend release acceptance | PASS — `mvn -f core/pom.xml -Dtest=FinalReleaseAcceptanceTest test`, 5/5 |
| Docker release check | NOT RUN — no container, build, or deployment input changed |

Raw Chrome output is committed at `EVIDENCE/playwright-form-layout-raw.json`; SHA-256
`96af3e425fb9f3b79cbd3157d40cf353ff0968daa8dd18bae1aba37ed336a668`. The checksum-bound
command, browser (`Chromium 151.0.7922.34`), source hashes, case set, and acceptance boundary are in
`EVIDENCE/playwright-form-layout-summary.json`.

The browser fixture intercepts console APIs and proves DOM/layout behavior only. It is not evidence
of live backend integration, permissions, persistence, or production data.
