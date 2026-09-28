# issue-108 Chrome Playwright evidence

Scope: GitHub issue `#108` — DeepSeek-Platform-aligned sign-in page and unified Admin/Tenant
console shell.

## Why a baseline is stored here

The repository's Chrome suite contains failures that exist on `origin/main` **before** any
issue-108 change. Those failures are environment- or data-dependent (they drive login against a
live backend at `http://127.0.0.1:8080`, which is not running in this workspace) or are
spec-versus-application mismatches that predate this issue. Without a recorded baseline they could
be mistaken for regressions introduced by the restyle.

## Commands

Baseline (branch `fix/108-deepseek-platform-shell` fast-forwarded to `origin/main`, before any edit):

```
cd web
YCSOPEN_E2E_ISOLATED=true npx playwright test --reporter=list
```

After implementation:

```
cd web
YCSOPEN_E2E_ISOLATED=true npx playwright test --reporter=list
```

## Result

| Run | Passed | Failed | Skipped | Did not run |
|---|---|---|---|---|
| Baseline (`origin/main`) | 142 | 15 | 3 | 41 |
| After issue #108 | 150 | 15 | 3 | 41 |

- `playwright-baseline-failing-list.txt` — the 15 failing tests at baseline.
- `playwright-after-failing-list.txt` — the 15 failing tests after the change.
- `diff` of the two normalised lists is **empty**: the failing set is identical, so no existing
  failure was introduced or masked. The 8 additional passing tests are
  `test/scripts/issue-108-deepseek-shell.spec.ts`.

`did not run` counts come from Playwright serial-mode abort: when a serial test in a file fails,
the remaining tests in that file are not executed. Both runs have the same shape, so the counts are
comparable.

## Failure categories carried from the baseline

| Category | Tests | Cause boundary |
|---|---|---|
| Drives real login with no route mock | `tenant-access.spec.ts` (6), `channel-configuration.spec.ts`, `channel-health.spec.ts`, `system-configuration.spec.ts` | Needs a live backend with seeded accounts at `127.0.0.1:8080`; not available here |
| Backend-dependent interaction timeout | `admin-operations.spec.ts` (3), `query-panel.spec.ts:216` | Same boundary |
| Spec references removed navigation test ids | `sidebar-navigation.spec.ts:4` | The Admin layout consolidated those detail links before this issue; the spec still asserts them |
| Upload-status fixture | `tenant-qualification.spec.ts:219` | Pre-existing application/spec mismatch |
