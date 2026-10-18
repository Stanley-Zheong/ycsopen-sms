# Issue 124 Design

Schema migrations: declared

## Scope Table

| Concern | Owner | Contract |
|---|---|---|
| Persisted case snapshot | Existing `complaints` row | Source, attribution, summary, current handling/closure evidence, state actors, and state timestamps remain the current-case snapshot. |
| Persisted remediation snapshot | Existing `disposal_records` rows | Remediation/recovery execution state and compensation fields remain authoritative for resource action and recovery eligibility. |
| Persisted event history | New `complaint_case_events` table | Append one immutable event per completed case command in the transaction that owns its durable result, including sanitized failed-remediation evidence; backfill only still-provable historical facts. |
| Derived timeline | `ComplaintCaseService.caseDetail` | Read the selected case plus its ordered append-only events and expose explicit event type and result. |
| Atomic state transition | `ComplaintCaseService.accept/handle/close/recover` and remediation row lock | Update only the expected current state; distinguish not-found from stale state; lock a handled complaint before remediation side effects. |
| Reference choices | `ComplaintCaseService.referenceOptions` | Return minimal tenant/channel/signature/template choice data. Tenant ownership is returned only for signature/template rows whose schema contains it. |
| Public API | `ComplaintCaseController` and `core/docs/API.md` | Add detail/timeline and reference-option reads; existing mutation paths remain compatible. |
| Browser workflow | `AdminComplaintsPage.tsx` | Intake, selected case, timeline, and action-specific dialog own their own loading, error, retry, validation, and submission states. |
| Compatibility | Existing complaint list and mutation clients | Existing response fields and mutation URLs remain; added case/remediation fields are additive. |
| Exclusions | Other compliance and resource modules | No lifecycle, remediation-target, ratio, or automatic intervention rule changes. |

## Ownership Matrix

| Rule | Owner |
|---|---|
| Case list, event append, detail/timeline assembly, reference choices, state compare-and-set | `ComplaintCaseService` |
| Authentication-derived actor and endpoint permissions | `ComplaintCaseController` |
| HTTP types and serialization | `web/src/api/complaintCaseApi.ts` |
| Selected-case state, action eligibility, contextual dialog, retries, and feedback | `AdminComplaintsPage.tsx` |
| Focus restoration and keyboard trapping | `ModalDialog.tsx` |
| Complaint page layout | `web/src/styles/alert-engine.css` |
| API and operator instructions | `core/docs/API.md`, `docs/使用手册.md` |

## State and Command Flow

1. The page loads complaint rows and minimal reference choices independently.
2. The intake form starts with placeholder selections and empty business values. An explicit submit creates one complaint and refreshes the list.
3. Selecting a complaint loads `GET /api/v1/console/complaints/{id}`. The workspace shows the selected case and its timeline; another selection replaces this context.
4. A state action is enabled only for the loaded row state. Opening it creates a new empty action draft tied to that row and displays the target and effect.
5. A synchronous client latch and pending-state controls allow one submission. Accept, handle, and close use compare-and-set updates. Remediation and recovery share a complaint-row lock: remediation checks `PROCESSED` before its resource side effect, while recovery checks `PROCESSED`/`CLOSED`, recalculates the latest `FAILED` disposal under the lock, and then uses disposal compare-and-set.
6. Success closes the dialog and refreshes the list plus selected-case detail. Failure retains the dialog evidence for correction; stale-state failure also refreshes server state.
7. Remediation recovery is available only for a persisted failed remediation in the selected case history.

## Action Eligibility

| Case/record state | ADMIN/OPERATOR action | Result | FINANCE |
|---|---|---|---|
| `PENDING` | Accept with required opinion | Case becomes `PROCESSING`; acceptance evidence is appended. | Read only |
| `PROCESSING` | Handle | Case becomes `PROCESSED`; handling evidence is appended. | Read only |
| `PROCESSED` | Remediate | Exact attributed resource is attempted; `APPLIED` or `FAILED` disposal/event evidence is persisted. | Read only |
| `PROCESSED` | Close | Case becomes `CLOSED`; closure evidence is appended. | Read only |
| `PROCESSED` or `CLOSED` with latest exact disposal record `FAILED` | Recover | Exact failed record becomes `RECOVERED`; a separate recovery event is appended without reopening the case. | Read only |
| Any other combination | None | Button is disabled and forged/stale commands are rejected. | Read only |

The selected-case detail is authoritative. List-row state may open the workspace, but cannot directly submit a mutation. Tenant changes clear signature and template selections that no longer belong to that tenant; channel is not tenant-filtered because its schema has no tenant relation. Empty selection stays empty, including after option refresh.

The detail response is `{ complaint, timeline, remediations }`; the options response is `{ tenants, channels, signatures, templates }`. Every option has `{ id, label, tenantId }`, where `tenantId` is null for tenants and channels and is populated only from the real signature/template owner column.

## Timeline Events and Backfill

`complaint_case_events` stores `complaint_id`, event type, actor/time, before/after status, evidence, target, result, review, failure, related disposal ID, plus non-null `source_record_type` and `source_record_id`. Runtime writes and backfill use the same identity map: `REGISTERED`, `ACCEPTED`, `HANDLED`, and `CLOSED` use `COMPLAINT` + complaint ID; `REMEDIATION_APPLIED`, `REMEDIATION_FAILED`, and `RECOVERED` use `DISPOSAL_RECORD` + disposal-record ID. A unique key on `(event_type, source_record_type, source_record_id)` prevents duplicate command/backfill events, while `(complaint_id, occurred_at, id)` provides deterministic timeline order. Legacy actor/evidence fields are nullable.

| Command or backfill fact | Timeline event |
|---|---|
| `created_at` / `created_by` | `REGISTERED`, state `PENDING`, source and summary evidence |
| Accept command | `ACCEPTED`, state `PROCESSING`, actor/time and the submitted acceptance opinion |
| `handled_at` / `handled_by` | `HANDLED`, state `PROCESSED`, opinion, remediation, and requirement |
| Remediation command | `REMEDIATION_APPLIED` or `REMEDIATION_FAILED`, reason, target, review, result, and failure evidence |
| Recovery command | `RECOVERED`, original remediation reference, recovery review, and condition without mutating the earlier event |
| `closed_at` / `closed_by` | `CLOSED`, state `CLOSED`, closure note |

Historical backfill uses the same event types but leaves evidence or review null when later snapshot updates erased the original value. Null means unavailable historical evidence; it is not presented as complete source data.

Legacy `failure_reason` values may contain raw downstream exception text from the pre-V6600 implementation. Backfill maps every non-null legacy failure to the fixed safe failure message, and the remediation read model applies the same projection. The raw value is neither copied into the event owner nor returned by the API.

## Failure Model

- List, reference-option, and case-detail failures render separate retry actions.
- A missing selected case returns the established complaint-not-found business error.
- A zero-row transition reads the case again: a missing row returns not-found, while an existing row returns HTTP 409 with `COMPLAINT_STATE_STALE`. The browser refreshes current state before another action.
- Invalid state buttons remain disabled, while the backend still rejects forged or stale commands.
- Closing or cancelling a dialog sends no request. Pending submit disables confirm, cancel, close, and Escape closure.
- Missing attribution remains explicit and does not select the first reference option.
- A failed downstream resource transaction exits before failure evidence is written. The enclosing command transaction retains the complaint-row lock and then persists the sanitized, bounded `FAILED` disposal row and `REMEDIATION_FAILED` event together; an integration test proves the evidence survives a rollback-only resource failure without exposing downstream exception text.
- Initial deployment disables complaint registration and mutation before V6600 is applied, keeps it disabled through migration/backfill and mixed-version rollout, and resumes only after every active instance writes events. A downgrade to a non-event-writing version uses the same full-window freeze. Read-only complaint access remains compatible. This prevents an unrecoverable history gap.

## Validation Ladder

- Migration/service: fresh-schema event table, honest historical backfill, complete new-command timeline with deterministic time/id ordering, recovered failure preservation, minimal choices, stale compare-and-set/row locking, and durable failure evidence after a real resource-transaction rollback.
- Transaction integration: deterministic remediation-versus-close and recovery-versus-new-remediation races prove the shared complaint lock, latest-failed check, CAS result, and event consistency.
- Method security: a Spring Security test with method security enabled proves FINANCE can read detail/timeline but cannot call mutations or reference choices.
- Controller/MockMvc: additive detail/timeline and choice serialization plus authenticated mutation actor.
- React: empty intake, source-backed choices, selected-case dialog, status eligibility, duplicate lock, timeline, retry, and stale feedback.
- Chrome: create-to-accept-to-handle-to-remediate-to-recovery-or-close user flow with target context and timeline readback.
- Regression: full backend/frontend suites, build, planning validators, diff hygiene, and independent final review.

The repository's complaint-management Google Chrome CI lane is the scoped browser check. The additive migration is verified on a fresh schema and an existing V5000 complaint schema; no seed, Compose service, runtime configuration, or packaged release identity changes in Issue 124.
