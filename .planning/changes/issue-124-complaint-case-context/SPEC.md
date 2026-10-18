# Issue 124 Complaint Case Context

GitHub issue `#124` amends the completed Phase 41 complaint-management module. The current page keeps handling, remediation, and recovery inputs in one page-level draft while row actions submit that draft to whichever complaint row was clicked. The page also exposes only the latest case snapshot and a truncated remediation readback, so an operator cannot inspect one case's complete handling history.

## Behavior

| Behavior ID | Required behavior | Observable acceptance |
|---|---|---|
| issue-124-case-intake | Complaint intake starts without invented tenant, channel, signature, template, message, mobile, summary, or requirement values. Source and content type use controlled values; tenant, channel, signature, and template use source-backed choices. | React and Chrome tests observe empty business inputs, option loading/error feedback, and a submitted payload containing only the operator's selections. |
| issue-124-case-context | A complaint row only opens its server-loaded single-case workspace. Accept, handle, remediation, recovery, and close commands open from that workspace and display the complaint ID, tenant, summary, current state, action, and effect before submission. Stage-specific inputs live only in that case action dialog. | The action dialog identifies the selected case, starts with empty evidence fields, blocks invalid or duplicate submission, and sends one command for the selected complaint. |
| issue-124-case-timeline | A selected complaint exposes a chronological timeline derived from the complete persisted case and remediation history. | The timeline shows registration, acceptance, handling, remediation success/failure, recovery, and closure when present, including actor, time, evidence, target, review identity, and result. |
| issue-124-case-state-feedback | The list, reference choices, selected case, and timeline expose loading, empty, error, retry, and stale-state feedback. Actions that are invalid for the current state are disabled. | Concurrent state rejection refreshes the case data and presents an actionable error; status-specific buttons never submit an illegal transition. |

## Scope

- Phase 41 complaint case service/controller read models and atomic state transitions.
- Additive complaint detail/timeline and complaint reference-option APIs.
- `/admin/complaints` intake, list, selected-case workspace, contextual action dialog, timeline, state feedback, and stable selectors.
- Focused Java, React, and local Google Chrome Playwright coverage.
- API documentation, user instructions, Phase 41 trace amendments, and frontend spirit 02 records.
- One additive, registered Flyway migration for the append-only case-event owner and honest legacy backfill.

## Out of Scope

- New complaint states, automatic complaint-ratio intervention, or a generic workflow engine.
- Changing tenant, channel, signature, template, blacklist, or recovery business policy.
- Rewriting applied Flyway migrations or changing existing complaint/disposal columns.
- Cross-browser certification beyond the repository's Google Chrome contract.
