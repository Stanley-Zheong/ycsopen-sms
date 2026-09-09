# Phase 11 UI Specification — Channel health, pools, and candidate pause

## Routes

- `/admin/channel/health` — channel health monitor, pause, maintenance, and
  eligibility feedback.
- `/admin/channel/pools` — pool list and pool editor.

## Chrome-only browser boundary

Production browser verification uses the installed local Google Chrome at
`/Applications/Google Chrome.app/Contents/MacOS/Google Chrome` with 1440x900
viewport. Mobile and non-Chrome browsers are outside the phase.

## Interaction contract

- Health page renders a dense table with health state, latest metrics, last
  check result, eligibility reason, pause/maintenance actions, and event count.
- Pause action opens a dialog requiring trigger and reason; submit records actor,
  trigger, reason, and time, then refreshes the row.
- Maintenance start opens a dialog requiring reason; maintenance end is disabled
  until a successful validation result exists.
- Pool page supports weighted and primary-backup modes. Weighted mode requires
  positive weights; primary-backup mode requires exactly one primary.
- Every actionable control has a stable `data-testid` recorded in
  `UI-ELEMENTS.md` and matched by Playwright.
