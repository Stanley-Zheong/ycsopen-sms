# Issue 91 Action Reason Review

## Review outcome

Live pull-request review reopened the affected slice after finding interaction,
target-description, error-group completeness, and value-mapping gaps. Those
findings were resolved, and commit
`bfc134a9db111bb2d66af5f070c230b3ec111bc2` passed every remote job. A later
live review then found message-operation retry identity and taxonomy join
multiplicity gaps. Those corrections passed independent review and the complete
remote gate on `42e588fd81229e91d0aaebdab35e4e3e88232a3e`; Core's only failure
was the deliberately open three-item TODO sentinel. A newer live review then
found that a same-ID retry could edit its reason even though the idempotent
backend retains the first audit value. Another asynchronous review found that
Escape or cancel could close the dialog in the synchronous window between the
submit latch and the owner's pending rerender. Both corrections now pass on
`fcb5038684fd8c80985b10002765856cc5e73a95`. A later current-head review found
that the null-derived display label `UNKNOWN` collided with a valid literal
upstream code and also detected an expired browser-evidence source digest. The
correction separates those aggregates with an explicit capability flag, tests
both cases, and reseals the browser evidence. Pull-request run `34773792381`
passed Web, both portable gates, Phase 03 real integration, and installed-Chrome
Docker fresh/upgrade/restart on commit `332570a`; Core executed 985 tests and
failed only the deliberately open TODO sentinel. The next live review found two
further gaps: message/status filters were applied to loaded send targets but not
to the aggregate used by the completeness guard, and the Docker browser case
reused the isolated UI obligation without its own atomic matrix/UI trace. The
current worktree applies the complete filter domain to both queries, proves the
message and non-failed-status cases, and assigns the real-service case its own
behavior, obligation, matrix row, and UI trace. A current-commit remote run and
live review remain open. Existing permissions, tenant boundaries, state
transitions, and production audit contracts remain unchanged.

Independent pre-push review of the corrected worktree found no actionable code
or documentation issue. It also read back all 15 evidence source digests after
the final isolated browser run. The subsequent real-service Docker result
closed that review's remote verification boundary; the later findings require a
new independent readback. That readback has now passed on the current worktree:
the reviewer confirmed stable retry identity, conservative one-row taxonomy
collapse, H2/MySQL SQL compatibility, focused test coverage, and all 15 source
digests. Pull-request run `34768234208` then passed the installed-Chrome real
service lane and all other remote jobs except the expected TODO sentinel. The
later immutable-reason correction has now passed another independent worktree
readback: first-submit trimming, failed-state immutability, same-ID/same-reason
retry, owner compatibility, browser/unit coverage, and all source digests were
confirmed. The synchronous close/cancel latch correction also passed independent
shared-component and recharge-owner review, including failure-release behavior
and both available modal close paths. Pull-request run `34770568655` passed the
Web, portable-contract, Phase 03 real-integration, and installed-Chrome Docker
jobs on that correction; Core executed 984 tests and failed only the deliberately
open TODO sentinel. Live asynchronous review then covered the full diff through
that exact head and reported no author-actionable issue, allowing the completion
checklist to close before the final Core rerun.

The null/literal-`UNKNOWN` correction passed a further independent readback. The
reviewer confirmed raw SQL grouping, distinct task counts, H2/MySQL syntax,
record serialization, null-safe frontend target binding, separate row identity,
backend/unit/Chromium coverage, and all 15 resealed source digests. The later
filter-domain and atomic-trace correction has passed its focused backend/UI
tests, full UI suite, build, lint, affected Chromium suite, planning-validator
self-test, and 15-digest readback. Its first independent review found that the
send-target query still omitted the error-code predicate and could lose the
selected group behind its 200-row limit. The follow-up adds the missing
predicate and a service case with 201 newer messages from another error group;
the reviewer reran the 9-test service suite, read back all 15 digests, and
reported PASS with no further finding. The remote executable boundary remains a
prerequisite for closing the TODO sentinel.

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
  pointer or keyboard double activation creates one request. Close, Escape, and
  cancel read the same latch, including before the pending prop rerenders.
  Pending focus moves to the read-only reason field and remains trapped in the
  dialog; a failed request releases the latch for retry.
- Message-operation retries retain both the operation ID and the trimmed reason
  captured by the first submission. After a failure the reason stays read-only,
  preventing the displayed retry intent from drifting from the persisted audit.
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
- Null stored error codes and the literal upstream code `UNKNOWN` now remain
  separate backend aggregates even though both share the normalized display
  label. The API exposes `bulkActionSupported`; the UI labels and disables only
  the null-derived placeholder, while a literal `UNKNOWN` action sends that
  exact code and excludes null-code messages. Backend, unit, and Chromium tests
  cover both rows.
- Error aggregates now apply the same message ID and status predicates as the
  loaded send targets, and send targets now apply the same error-code predicate
  as the aggregate. A message-specific query therefore compares one target with
  a one-task aggregate, any non-`FAILED` status yields no failure group, and 201
  newer messages from another error code cannot displace the selected targets
  from the 200-row send window. Backend and UI unit tests cover these scope
  rules.
- Alert mute confirmation now names global alert notification as the target,
  keeps the chosen alert only as initiating context, and states that all new
  alert notifications are suppressed for 30 minutes.
- Docker acceptance now has its own atomic behavior, obligation, matrix row,
  and UI trace. It includes all nine routes in installed Google Chrome against
  real Web/Core services, plus a test-owned recharge approval with double-click
  request counting and persisted reason/balance-audit readback.

## Tool boundary

The repository-required tool-less Claude review was attempted with the complete
tracked and untracked diff, but the CLI stopped before reading it with
`Not logged in · Please run /login`. This is recorded as an authentication
boundary, not as a successful review. The independent pre-push review and the
executable gates in `VERIFICATION.md` provide the completed replacement review
evidence.
