# Spirit 02: Admin Operations Spec

## Intent

Make operations pages understandable and action-complete for alerts, complaints, status changes, and reason-bearing state transitions.

## Scope

### In

- Alert rules, notification settings, and alert history action semantics.
- Complaint management page behavior and complaint attribution states.
- Tenant/account/channel/status row actions that require target-aware confirmation.
- Follow-up from issues `#90`, `#91`, `#92`, and open admin operations layout findings.

### Out

- Finance billing policy.
- Message delivery workbench exports and resend mechanics unless they are reused confirmation behavior.
- Backend persistence changes not required by the selected issue.

## Behavior

| Behavior ID | Required behavior | Observable acceptance |
|---|---|---|
| FE-SPIRIT-02-ALERT-ACTIONS | Alert buttons use explicit verbs such as confirm, resolve, mute, edit, retry notification, and show the result of each action. | A user can predict the state change before clicking; Playwright observes the state or request payload after confirmation. |
| FE-SPIRIT-02-COMPLAINTS | Complaint pages show attribution completeness, status, required next action, and escalation or closure result. | Unknown attribution appears as a distinct state and cannot be counted as normal attribution. |
| FE-SPIRIT-02-ROW-STATUS | Row-level status actions are available on each eligible row and bind the target row in the confirmation dialog. | Each sampled row action shows target, effect, reason if required, and submits only one request. |

## Remaining TODO

- Open item: Select owning issue for the first admin operations implementation PR.
- Open item: Map alert history buttons to exact action names and API effects.
- Open item: Record final verification commands in `QUALITY-GATEWAY.md`.
