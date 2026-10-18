# Issue 120 Verification

| Claim | Command or observation | Status | Result and boundary |
|---|---|---|---|
| Joined backend identity and API JSON | `mvn -f core/pom.xml -Dtest=UplinkNormalizationServiceTest,UplinkNormalizationControllerTest test` | executed-pass | 7 tests passed for list/detail/monitor projection, absent-master fallback, bounded lookup, permission surface, and JSON serialization. |
| Final frontend behavior | `npm --prefix web test -- test/unit/uplink-normalization.test.tsx` | executed-pass | 7 tests passed for display/fallback, independent list and monitor lookups, stable-ID requests, ambiguity, lookup failure, repeated invalid submit, and loading-to-valid retry. |
| Production compilation | `npm --prefix web run build` | executed-pass | TypeScript and Vite build passed; the existing chunk-size advisory is non-blocking. |
| Issue Chrome behavior | `npm --prefix web run test:e2e -- uplink-normalization.spec.ts --project=local-google-chrome --workers=1 --grep pw-issue-120` | executed-pass | All three scenarios passed in the combined implementation run. After refresh-state hardening, identity and feedback passed again on the final diff; the direct-filter scenario is outside the changed refresh branch. A later isolated retry had no test verdict because the long-lived container could no longer clean up Chrome processes. |
| Diff hygiene | `git diff --check` | executed-pass | Complete issue diff passed. |
| Full frontend regression | `npm --prefix web test`; isolated `npm --prefix web test -- test/unit/identity-pages.test.tsx` | executed-fail | Full local run reached 222/223 before an unrelated identity test timed out under constrained parallelism; the same file then passed 8/8 in isolation. GitHub Node 20 is authoritative. |
| Full backend regression | `mvn -f core/pom.xml test` | executed-fail | 853 tests started, but missing Ruby/integration DNS and unreaped process trees caused environment failures before Surefire was killed with exit 137. GitHub Java 21 and integration jobs are authoritative. |
| Planning validator | `/usr/bin/env ruby .planning/tools/test-planning-validators.rb` | blocked | This runtime has no Ruby executable. GitHub Phase 03 portable contracts owns this gate. |
| Docker release | Pull-request Docker release / Google Chrome job | blocked | Docker is unavailable locally. The unchanged repository lane will run on the pull request because this diff changes `web/`. |

The local full-suite boundaries do not replace CI. Merge remains blocked until every required pull-request check passes.
