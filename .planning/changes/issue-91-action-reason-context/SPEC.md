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
| issue-91-action-reason-context | A reason input is absent until an authorized operator chooses a concrete state-changing action. The resulting modal names the action and target, explains the effect, blocks interaction with underlying page actions, and provides an empty required reason field. Cancel closes the modal without a request. Confirm is disabled while the trimmed reason is empty. Confirmation is synchronously latched so a double click or repeated keyboard activation produces one request. While a request is pending, focus remains inside the modal and confirm, cancel, keyboard dismissal, and background action replacement remain blocked; a failed request releases the latch for an explicit retry of the same selected operation. Message-operation retry reuses both its idempotency ID and the first submitted reason snapshot, which remains read-only until cancellation or success. Confirmation submits the entered reason to the selected action and target. | Isolated browser coverage traverses recharge review, uplink replay and push control, message/receipt/error operations, alert resolve and global mute, failed-push control, and admin send-task control. Each page has no detached reason field before action selection; each sampled action opens the correct modal, exposes its target/effect, blocks an empty confirmation, and sends exactly the entered reason only after confirmation. Shared-component and browser tests prove duplicate-activation, stable retry identity and reason, and pending focus/dismissal locks. Docker acceptance uses installed Google Chrome against the real Web/Core services to prove all affected routes start without detached reason fields, then performs one recharge approval and reads back its persisted reason and balance audit. |

## Scope

- `/admin/tenant-recharge-review`: approve and reject one recharge request.
- `/admin/uplink`: replay one uplink record and replay, pause, or resume one
  push event.
- `/admin/submission/details`, `/admin/send/details`,
  `/admin/receipt/details`, and `/admin/error/details`: export, resend, appeal,
  receipt correction/replay, bulk retry, and problem marking.
- Export confirmation describes the backend export type instead of the visible
  tab: send and receipt tabs export that detail type, while submission and
  error tabs export the combined send, receipt, and submission dataset. Because
  the existing export service does not filter by error code, a page error-code
  filter is explicitly identified as excluded and is not submitted as part of
  the export snapshot.
- Error bulk actions belong to the error-group row whose action was chosen. The
  dialog and request use that row's error code and only currently loaded failed
  messages with the same code. The backend aggregate counts each failed task
  once even when multiple active provider/protocol taxonomy mappings share its
  code; conflicting mapping attributes collapse to a conservative category,
  severity, and retry policy instead of duplicating the group. The loaded target
  count must equal that distinct-task aggregate, so a list truncated by its
  independent 200-row limit cannot become a partial submission. Groups exceeding
  the backend limit of 50 are not partially submitted. The display-only
  `UNKNOWN` group represents a null stored error code and cannot be passed
  losslessly to the existing bulk API, so its actions remain unavailable. While
  send targets are loading, when target loading fails, when loaded targets are
  incomplete, or when a group has zero or more than 50 matching failed messages,
  its bulk controls are disabled and no dialog or request may be created.
- `/admin/alerts`: resolve one alert, or start a 30-minute global notification
  mute from one alert row. The mute target and consequence name its global
  scope; the selected alert remains visible only as the initiating context.
  Acknowledge remains an immediate action because it has no reason parameter.
- `/admin/push/failures`: replay, pause, or resume one failed delivery.
- `/admin/send/jobs`: pause, resume, cancel, or restart one send task.
- Preserve current action-trigger selectors and current API payload contracts.
- Message-operation actions allocate their existing `actionId` when the dialog
  opens and snapshot the trimmed reason on first confirmation. An explicit retry
  after an ambiguous client failure retains both values and keeps the reason
  read-only; cancellation, success, or selecting a new target ends that operation
  identity and permits a new reason.
- Remove all prefilled audit reasons from these pages; placeholder copy must
  explain what evidence the operator should enter without becoming submitted
  data.
- Preserve existing persistence limits: recharge review and alert reasons are
  limited to 255 characters; message operations, webhook/uplink controls, and
  bulk-task controls retain the shared 500-character limit.

## Verification boundary

The cross-route Issue #91 Playwright case renders the current React application
in Chromium and uses deterministic responses for existing business APIs. It
proves detailed DOM, modal, focus, duplicate-activation, action/target binding,
validation, cancellation, and request-payload behavior without claiming service
persistence. A complementary Docker case runs installed Google Chrome against
the real Web/Core services: it proves the initial no-detached-reason state on
all affected routes and performs a recharge approval with persisted reason and
balance-audit readback. Existing backend tests remain authoritative for other
unchanged state, permission, persistence, and audit contracts.
