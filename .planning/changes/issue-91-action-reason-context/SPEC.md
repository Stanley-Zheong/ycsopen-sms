# Issue 91 Contextual Action Reasons

GitHub issue #91 amends the shared state-changing action contract for the
current operations console. Several completed phase pages expose one reason
field before an operator chooses an action or target. The field is therefore
detached from the request it audits, and its prefilled value can be submitted
without an explicit operator statement.

This change applies the repository's existing confirmation-dialog pattern to
the affected admin pages. Backend APIs, permissions, state-transition rules,
idempotency boundaries, and audit persistence remain page-owned and unchanged.

## Behavior

| Behavior ID | Required behavior | Observable acceptance |
| --- | --- | --- |
| issue-91-action-reason-context | A reason input is absent until an authorized operator chooses a concrete state-changing action. The resulting modal names the action and target, explains the effect, blocks interaction with underlying page actions, and provides an empty required reason field. Cancel closes the modal without a request. Confirm is disabled while the trimmed reason is empty. While a request is pending, confirm, cancel, keyboard dismissal, and background action replacement remain blocked; confirmation submits the entered reason to the selected action and target. | Chrome covers recharge review, uplink replay and push control, message/receipt/error operations, alert resolve and mute, failed-push control, and admin send-task control. Each page has no detached reason field before action selection; each sampled action opens the correct modal, exposes its target/effect, blocks an empty confirmation, and sends exactly the entered reason only after confirmation. Shared-component tests prove the pending-state dismissal lock; the Chrome case proves that a pending action cannot be replaced from the background. |

## Scope

- `/admin/tenant-recharge-review`: approve and reject one recharge request.
- `/admin/uplink`: replay one uplink record and replay, pause, or resume one
  push event.
- `/admin/submission/details`, `/admin/send/details`,
  `/admin/receipt/details`, and `/admin/error/details`: export, resend, appeal,
  receipt correction/replay, bulk retry, and problem marking.
- Error bulk actions belong to the error-group row whose action was chosen. The
  dialog and request use that row's error code and only currently loaded failed
  messages with the same code. While send targets are loading, when target
  loading fails, or when a group has no matching failed message, its bulk
  controls are disabled and no dialog or request may be created.
- `/admin/alerts`: resolve or mute one alert. Acknowledge remains an immediate
  action because it has no reason parameter.
- `/admin/push/failures`: replay, pause, or resume one failed delivery.
- `/admin/send/jobs`: pause, resume, cancel, or restart one send task.
- Preserve current action-trigger selectors and current API payload contracts.
- Remove all prefilled audit reasons from these pages; placeholder copy must
  explain what evidence the operator should enter without becoming submitted
  data.
- Preserve existing persistence limits: recharge review and alert reasons are
  limited to 255 characters; message operations, webhook/uplink controls, and
  bulk-task controls retain the shared 500-character limit.

## Verification boundary

The Issue #91 Playwright case renders the current React application in Chrome
and uses deterministic responses for existing business APIs. It proves DOM
visibility, modal semantics, action/target binding, empty-reason blocking,
cancel behavior, and submitted reason payloads. Existing backend tests remain
the authority for state transitions, permissions, persistence, and audit
effects; no backend contract changes in this issue.
