# Issue 87 Review

## Findings resolved before the post-#108 rebase

- BLOCKER: the raw Playwright report existed only in `/tmp`. Resolution target: commit the final
  raw JSON under `EVIDENCE/` and bind the summary to its SHA-256.
- HIGH: the select fixture did not exercise the desktop 30% cap. Resolution: retain five static
  option-fit selectors and add one data-backed channel option that exceeds the cap.
- HIGH: the selector inventories in UI-ELEMENTS and TEST-MATRIX differed. Resolution: both artifacts
  now name the same five static selectors plus the channel-pool selector.

## Final review

Two independent reviewers examined the frozen implementation against the live issue and current
`main`. Their findings were resolved before the final evidence run:

- HIGH: four QueryPanel tracks were narrower than the historical side-by-side label/control
  minimum, while the first acceptance assertion checked only field origins. Resolution: Admin
  labels stack above controls at 1440px and Chrome checks every direct child stays inside its cell.
- HIGH: option-fit evidence covered only five named selects. Resolution: the 56-route traversal
  checks every visible single-value select that can fit within the cap; the long data-backed option
  independently proves cap behavior.
- HIGH: the initial four-column allowlist also selected card/panel grids and 720px modal grids.
  Resolution: only field grids remain in the allowlist, with explicit 2/3-column regression checks
  for three page-owned grids and two dialogs.
- HIGH: the nested postpaid `.trial-prepaid-form` occupied one outer track and subdivided it again.
  Resolution: it spans the outer grid and is checked for full width, four tracks, and containment.
- HIGH: three historical QueryPanel cases still expected left/right label geometry at 1440px.
  Resolution: they now assert stacked geometry at 1440px and retain a left/right assertion at
  1201px; issue #58's durable contract records the supersession.
- MEDIUM: the test matrix named four Playwright IDs although the implementation intentionally uses
  one route-efficient contract test with three steps. Resolution: the matrix now maps each
  obligation to `pw-issue-87-admin-form-contract` and its owning step.

Final verdict: no remaining BLOCKER or HIGH finding. The optional deterministic
`hengshi-precheck-diff` helper was unavailable in this runtime; both reviewers completed their
read-only source/diff review, and the repository's required checks are recorded in `VERIFICATION.md`.
