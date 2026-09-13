# Issue 91 Action Reason Review

## Review outcome

Independent affected-slice review passed with no remaining actionable findings.
The review covered the durable interaction contract, shared dialog, all six
owning page modules, focused unit tests, and cross-route Playwright coverage.
Existing API payload shapes, permissions, tenant boundaries, state transitions,
and audit contracts are unchanged.

The first remote CI run also exposed that the Docker Web prebuild used the
pull-request merge SHA even though the release check and checkout use the PR
head. The workflow now passes that head explicitly as `VITE_BUILD_COMMIT`;
the release identity assertion remains strict.

The next Docker run passed that browser identity check and reached the release
seed assertions. Its prefix check counted the same prefix across two independent
versions even though the schema key is `(version_id, prefix)`. The assertion is
now scoped to the release version and continues to require exactly one matching
release row.

## Findings resolved

- Pending submissions now reject Escape, cancel, close, and background actions.
- Recharge-review and alert reasons respect their 255-character persistence
  limit; other owners keep the shared 500-character default.
- Export actions snapshot the applied filters, display that target in the
  dialog, and submit the same snapshot.
- The reason field's accessible description includes both target and effect.
- Unrelated alert save, evaluate, or acknowledge completions cannot close a
  newly opened resolve or mute dialog.
- The full-viewport modal backdrop intercepts pointer input instead of relying
  only on visual stacking.
- Browser tests prove interaction behavior through roles and stable test IDs;
  they do not treat a CSS class assertion as interaction evidence.
- Pull-request review found that the original error-page bulk controls could
  label the dialog with one error code while submitting failed messages from
  other groups. The controls now live on each error-group row, snapshot only
  loaded failed messages with that row's code, and stay disabled during target
  loading, after target-query failure, or when no target matches. Unit and
  Chromium tests cover multiple groups, exact payload selection, loading,
  query failure, and empty-target behavior.

## Tool boundary

The repository-required tool-less Claude review was attempted with the complete
tracked and untracked diff, but the CLI stopped before reading it with
`Not logged in · Please run /login`. This is recorded as an authentication
boundary, not as a successful review. Independent pre-push review and the
executable gates in `VERIFICATION.md` provide the completed review evidence.
