# Spirit 04 Quality Gateway

## Required Commands

| Gate | Command | Required result | Evidence |
|---|---|---|---|
| Diff hygiene | `git diff --check` | Pass | Issue `#120` complete diff passes. |
| Unit tests | `npm --prefix web test` plus targeted workbench tests | Pass | Issue `#120` focused suite passes 7/7; full-run boundary is recorded below. |
| Build | `npm --prefix web run build` | Pass | Issue `#120` production build passes. |
| Chrome Playwright | Workbench route coverage for selected issue | Pass | Issue `#120` list/detail/monitor/filter/feedback cases have split-run Chrome evidence below. |
| Backend checks | `mvn -f core/pom.xml test` when command API behavior changes | Pass or not applicable reason | Issue `#120` focused service/API suite passes 7/7; full-run boundary is recorded below. |

## Issue #120 Gate

| Gate | Command | Status | Evidence boundary |
|---|---|---|---|
| Dependency install | `npm --prefix web ci` | PASS | Installed 357 lockfile-defined packages with Node 20+; audit reported the repository's existing dependency advisories and made no lockfile change. |
| Targeted backend | `mvn -f core/pom.xml -Dtest=UplinkNormalizationServiceTest,UplinkNormalizationControllerTest test` | PASS | 7 tests: tenant projection, missing metadata, push-monitor projection, option lookup, and response serialization. |
| API serialization | Same focused Maven command | PASS | `UplinkNormalizationControllerTest` passed its list/detail/monitor/options JSON assertions through MockMvc. |
| Full backend | `mvn -f core/pom.xml test` | LOCAL ENVIRONMENT BOUNDARY; CI REQUIRED | 853 tests started; the container lacks Ruby and required integration configuration/DNS, cannot reap owned process trees, and eventually killed Surefire with exit 137. The issue-owned 7 tests pass; the GitHub Java 21 job remains the authoritative full-suite gate. |
| Targeted frontend | `npm --prefix web test -- test/unit/uplink-normalization.test.tsx` | PASS | 7 tests cover identity surfaces, fallback, independent filters, true backend matching, repeated invalid submit, loading-to-valid submit, and stable-ID requests. |
| Full frontend | `npm --prefix web test` | LOCAL CONCURRENCY BOUNDARY; CI REQUIRED | 222/223 passed; one unrelated Phase 5 identity test timed out under the constrained full run and then passed 8/8 in isolation. The GitHub Node 20 job remains the authoritative full-suite gate. |
| Production build | `npm --prefix web run build` | PASS | TypeScript project build and Vite production bundle completed; only the existing chunk-size advisory was emitted. |
| Chrome Playwright | `npm --prefix web run test:e2e -- uplink-normalization.spec.ts --project=local-google-chrome --workers=1 --grep pw-issue-120` | PASS (split-run evidence) | The three issue cases first passed together. After refresh-state hardening, the final-diff rerun passed identity and feedback, including missing-name, ambiguity, lookup-error, and loading-to-valid behavior; the direct filter/option path was unchanged and retains its prior Chrome result. A subsequent isolated retry was terminated by this container's accumulated Chrome process-cleanup failure before it produced a test verdict. Final React tests cover the changed repeated-submit branch; GitHub CI remains the clean-environment regression gate. |
| Planning validators | `/usr/bin/env ruby .planning/tools/test-planning-validators.rb` | LOCAL ENVIRONMENT BOUNDARY; CI REQUIRED | The runtime has no `ruby` executable. The GitHub portable-contract job installs/runs the repository validator toolchain and is the authoritative gate. |
| Diff hygiene | `git diff --check` | PASS | Complete issue `#120` diff. |
| Docker release | Existing repository lane; no issue-specific fixture | CI REQUIRED | No schema, seed, container, runtime configuration, or release packaging changes. The unchanged Docker release lane runs for the web diff in the pull request; focused MockMvc and targeted local Chrome provide issue-specific behavior evidence. |

## Merge Gate

- Gate item: Commands disclose target and snapshot.
- Gate item: Bulk actions cannot submit partial or incomplete targets.
- Gate item: Exports disclose included datasets and excluded filters.
