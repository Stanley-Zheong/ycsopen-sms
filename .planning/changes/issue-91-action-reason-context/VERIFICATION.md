# Issue 91 Action Reason Verification

## Executed passes

| Surface | Command | Result |
| --- | --- | --- |
| Dependency install | `npm --prefix web ci` | PASS; lockfile install completed. The audit reported 7 existing dependency advisories. |
| Focused affected UI tests | From `web/`: `npm test -- test/unit/action-reason-dialog.test.tsx test/unit/tenant-recharge.test.tsx test/unit/uplink-normalization.test.tsx test/unit/message-operations.test.tsx test/unit/alert-engine.test.tsx test/unit/webhook-delivery.test.tsx test/unit/bulk-scheduled.test.tsx` | PASS, 7 files / 28 tests. Shared-component and recharge-owner cases prove that Escape and cancel cannot close the dialog in the synchronous latch window before `pending` rerenders. The message-operation cases prove that an explicit retry after an ambiguous failure reuses the selected operation ID and first submitted reason, while the reason stays read-only. The error-group cases prove that aggregate/list count mismatches and the display-only `UNKNOWN` group fail closed without an API call. The final full run below includes the same focused coverage. |
| Full UI unit tests | `npm --prefix web test` | PASS, 49 files / 170 tests after the executable diff was frozen. Existing jsdom connection messages and React Router future warnings did not fail the run. |
| Production build | `npm --prefix web run build` | PASS; Vite emitted only its existing large-chunk advisory. |
| Changed-file lint | From `web/`: `npx --yes --prefix web eslint` over all changed TypeScript and TSX files with `--max-warnings 0` | PASS. |
| Isolated Issue #91 interaction | See the exact command in `EVIDENCE/playwright-action-reason-report.json`. | PASS, 1/1 in actual `Chromium/151.0.7922.34` after the executable diff was frozen; the case traverses all nine affected routes and checks visibility, context, validation, cancellation, double-activation request deduplication, same-ID/same-reason retry and reason immutability after an ambiguous failure, pending focus containment and dismissal locking, truthful export scope, selected error-group payload binding, empty/truncated/`UNKNOWN` group disabling, global mute scope, and success close. The unit cases above isolate the narrower synchronous pre-pending dismissal window. |
| Affected phase Playwright regression | Same accepted Chromium environment; `npm --prefix web run test:e2e` over `tenant-recharge.spec.ts`, `uplink-normalization.spec.ts`, `message-operations.spec.ts`, `alert-engine.spec.ts`, `webhook-delivery.spec.ts`, `bulk-scheduled.spec.ts`, `secure-async-export.spec.ts`, and `issue-91-action-reason-context.spec.ts`, `--workers=1 --reporter=line` | PASS, 27/27 after the executable diff was frozen. Ancillary unmocked dashboard requests emitted non-blocking Vite proxy connection messages; every business API exercised by the cases was intercepted. |
| Planning validator self-test | `/tmp/issue79-ruby.bBM4HQ/bin/ruby .planning/tools/test-planning-validators.rb` | PASS. A temporary user-space Ruby was used because the base image has no system Ruby. |
| Docker Web build identity | `VITE_BUILD_COMMIT=1111111111111111111111111111111111111111 npm --prefix web run build` followed by an exact meta-tag readback | PASS; the workflow now injects the checked-out pull-request head instead of the synthetic pull-request merge SHA. |
| Latest-main integration | Rebase onto `66d9cde07e957e9aa5597a434dec5e2fb6c1a8fd`, conflict review, the affected checks below, and the Issue #91 acceptance command recorded in evidence | PASS; incoming #93 also extended the release seed, release script, and Docker Chrome spec. The integration preserves both account-status and action-reason fixtures/cases; Docker discovery lists all three cases, and isolated Chromium acceptance passed 1/1 on the updated base. |
| Docker release seed identity | `bash -n scripts/verify-docker-release` and review against the release migration's `UNIQUE(version_id, prefix)` key | PASS; the fixture assertion now counts `1380013` only within `DEV-PREFIX-2026-09`, while still requiring exactly one release row. |
| Affected backend seed and service tests | `mvn -f core/pom.xml -Dtest=ReleaseAcceptanceSeedMigrationTest,TenantRechargeOperationsMigrationTest,TenantRechargeServiceTest test` | PASS, 6/6; this covers additive and repeatable release-fixture creation, the recharge state transition, and persisted audit behavior used by Docker acceptance. |
| Error aggregation service tests | `mvn -f core/pom.xml -Dtest=MessageReceiptErrorOperationsServiceTest test` | PASS, 7/7. The added case proves that two active provider/protocol mappings with the same provider code do not multiply task counts and that conflicting taxonomy collapses to the conservative category, highest severity, and non-retryable result. |
| Docker acceptance discovery | `npm --prefix web run test:docker-release -- --list` | PASS, 3 tests discovered; the existing Issue #60 identity case, incoming Issue #90 account-status case, and Issue #91 real-service action-reason case coexist. |
| Real-service installed-Chrome acceptance | Pull-request CI run [34770568655](https://github.com/Stanley-Zheong/ycsopen-sms/actions/runs/34770568655), `Docker release / Google Chrome`, commit `fcb5038684fd8c80985b10002765856cc5e73a95` | PASS. Fresh, upgrade, and restart each ran 3/3 cases against real Web/Core/MySQL with installed Google Chrome 152. The three JSON report SHA-256 values are `a0fd1f37da190fb417fd588990314d843e1b7aae2058ac4821369cb424ed3ac9`, `5fa4227b27bbbea40fa4afa1b37cd90fc43c6f9cadf95b863ad8e629c53ff4cf`, and `aa0de2e40e840f2e72a81b62a08462e16470faeff7b00a98c9a946f6022ddd8a`. The same run's Web, portable-contract, and Phase 03 real-integration jobs passed. Its Core job executed 984 tests with one failure and no errors: only the intentionally open three-item TODO sentinel. |

## Executed boundaries

- The final post-closure `mvn -f core/pom.xml test` compiled all backend source
  and executed 984 tests, ending with 7 failures, 4 errors, and 33 skipped. The
  release sentinel passed after observing the completed TODO list. Every
  remaining failure is a host/baseline boundary in untouched code: four Phase
  01 process-harness failures cannot find a system Ruby, Phase 08 has one
  failure and two errors because this host's PID 1 does not reap owned
  descendants, and the production migration-composition suite has two failures
  and two errors because migration runtime configuration is unavailable.
- `bash skills/code-review/scripts/precheck.sh --base origin/main` passed all
  whitespace checks, then stopped because its repository-wide Java scan found
  direct console writes already present on `origin/main`. The focused backend
  service test above is the executable gate for this change's SQL aggregation.
- The required tool-less Claude CLI review was executed with the complete
  tracked and untracked diff but exited before reading the patch with
  `Not logged in · Please run /login`. This authentication boundary is not
  represented as a successful Claude review.
- The first pull-request CI run exposed two non-product failures. `Web / Node
  20` timed out after five seconds in the untouched `identity-pages` test while
  the other 157 tests passed. `Docker release / Google Chrome` correctly
  rejected a Web build stamped with the synthetic pull-request merge SHA rather
  than the checked-out head. The workflow identity injection was corrected
  without weakening that release assertion; the replacement CI result is the
  authoritative remote gate.
- The second Docker run passed its real-Google-Chrome browser case, then exposed
  a seed check that counted `1380013` across both the development prefix version
  and the release prefix version. The schema's natural key is
  `(version_id, prefix)`, so the check now scopes the exact-one assertion to
  `DEV-PREFIX-2026-09`. It still fails on missing or duplicate release rows; the
  next complete remote run is authoritative.

## Acceptance scope

The Playwright case executes the current React application in a real Chromium
browser and isolates existing APIs with deterministic route responses. It
proves the Issue #91 UI interaction contract, not real-service authorization,
state transitions, persistence, or audit effects. Existing backend contracts
and tests remain authoritative for those unchanged surfaces.

The Docker release suite now contains a second, complementary acceptance lane:
installed Google Chrome traverses all nine routes against real Web/Core services
and approves one test-owned recharge fixture with persisted reason and balance
audit readback. Pull-request run `34770568655` passed this lane in fresh,
upgrade, and restart environments on the current executable code commit recorded
above, including the retry-identity, taxonomy-aggregation, immutable-reason, and
synchronous pre-pending dismissal corrections. The intentionally open TODO
sentinel was the only failed remote Core assertion. Live review then reached
this exact head with no author-actionable finding, so the completion checklist
was closed for the final Core rerun.

## Post-closure backend rerun

After all seven TODO items were closed on the final worktree,
`mvn -f core/pom.xml test` executed 984 tests with 7 failures, 4 errors, and 33
skipped. The release-sentinel failure tied to this change disappeared. The
remaining result exactly matches the untouched host/baseline boundaries above:
four Phase 01 failures, one Phase 08 failure plus two errors, and two
migration-composition failures plus two errors. Pull-request CI is the
authoritative clean host; its pre-closure Core run passed all product assertions
and failed only the TODO sentinel, and the post-closure run must pass before
merge.
