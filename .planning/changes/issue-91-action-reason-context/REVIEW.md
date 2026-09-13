# Issue 91 Action Reason Review

## Review outcome

Live pull-request review reopened the affected slice after finding interaction,
target-description, error-group completeness, and value-mapping gaps. Those
findings were resolved, and commit
`bfc134a9db111bb2d66af5f070c230b3ec111bc2` passed every remote job. A later
live review then found message-operation retry identity and taxonomy join
multiplicity gaps. Those corrections passed independent review and the complete
remote gate on `42e588fd81229e91d0aaebdab35e4e3e88232a3e`; Core's only failure
was the deliberately open three-item TODO sentinel. Existing API shapes,
permissions, tenant boundaries, state transitions, and production audit
contracts remain unchanged.

Independent pre-push review of the corrected worktree found no actionable code
or documentation issue. It also read back all 15 evidence source digests after
the final isolated browser run. The subsequent real-service Docker result
closed that review's remote verification boundary; the later findings require a
new independent readback. That readback has now passed on the current worktree:
the reviewer confirmed stable retry identity, conservative one-row taxonomy
collapse, H2/MySQL SQL compatibility, focused test coverage, and all 15 source
digests. Pull-request run `34768234208` then passed the installed-Chrome real
service lane and all other remote jobs except the expected TODO sentinel, so the
final TODOs can close.

After rebasing onto `66d9cde`, a second independent review passed the additive
resolution of Issue #90 and Issue #91 changes in the shared release seed,
release script, and Docker browser spec. It confirmed all three release browser
cases remain discoverable and the H2 seed test protects both fixtures.

The first remote CI run also exposed that the Docker Web prebuild used the
pull-request merge SHA even though the release check and checkout use the PR
head. The workflow now passes that head explicitly as `VITE_BUILD_COMMIT`;
the release identity assertion remains strict.

The next Docker run passed that browser identity check and reached the release
seed assertions. Its prefix check counted the same prefix across two independent
versions even though the schema key is `(version_id, prefix)`. The assertion is
now scoped to the release version and continues to require exactly one matching
release row.

The authoritative Docker job passed the fresh, upgrade, and restart lanes. Each
lane ran the Issue #60, #90, and #91 cases in installed Google Chrome against
real Web/Core/MySQL services; the Issue #91 case persisted the reason and read
back exactly one matching balance-audit entry.

## Findings resolved

- Pending submissions now reject Escape, cancel, close, and background actions.
- Confirmation is synchronously latched before the owner mutation begins, so
  pointer or keyboard double activation creates one request. Pending focus moves
  to the read-only reason field and remains trapped in the dialog; a failed
  request releases the latch for retry.
- Recharge-review and alert reasons respect their 255-character persistence
  limit; other owners keep the shared 500-character default.
- Export actions identify the real backend dataset for all four message tabs,
  remove unsupported error-code filtering from the submitted snapshot, and
  explicitly disclose that exclusion. The error tab also states that combined
  message-operations exports do not contain error aggregation rows.
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
  query failure, empty-target, and over-50 backend-limit behavior.
- Error-group bulk controls additionally compare the aggregate group count with
  the complete loaded target count, so the independent 200-row send-list limit
  cannot produce a partial submission. The display-only `UNKNOWN` group is also
  unavailable because it represents a null database value that the existing
  bulk API cannot accept losslessly. Unit and Chromium tests cover both guards.
- Alert mute confirmation now names global alert notification as the target,
  keeps the chosen alert only as initiating context, and states that all new
  alert notifications are suppressed for 30 minutes.
- Docker acceptance now includes all nine routes in installed Google Chrome
  against real Web/Core services, plus a test-owned recharge approval with
  double-click request counting and persisted reason/balance-audit readback.

## Tool boundary

The repository-required tool-less Claude review was attempted with the complete
tracked and untracked diff, but the CLI stopped before reading it with
`Not logged in · Please run /login`. This is recorded as an authentication
boundary, not as a successful review. The independent pre-push review and the
executable gates in `VERIFICATION.md` provide the completed replacement review
evidence.
