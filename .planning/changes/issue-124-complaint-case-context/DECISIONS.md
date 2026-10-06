# Issue 124 Decisions

## DR-124-001: One Case Owns Each Action Draft

State-changing fields are created when an operator opens an action for one complaint and are discarded when the dialog closes. The dialog names the case, tenant, summary, current status, requested action, and expected result. No handling or remediation input remains at page scope.

## DR-124-002: Persist Append-Only Case Events

`complaints` stores only the latest handling evidence, and recovery replaces fields on one `disposal_records` row. Those mutable snapshots cannot prove every event after later actions.

Add `complaint_case_events` as the append-only audit owner for registration, acceptance, handling, remediation, recovery, and closure. Each completed command writes its event in the transaction that owns its durable result. Successful remediation writes the resource change, disposal, and event atomically; a failed resource attempt rolls back those writes before the still-locking command transaction records sanitized failure evidence. The migration backfills only facts still provable from existing complaint and disposal rows; it does not invent overwritten acceptance evidence or an overwritten original remediation review ID.

## DR-124-003: State Transitions Use Compare-and-Set Updates

Accept, handle, close, and recovery update only the expected current state or exact failed disposal record. A zero-row update means the case or remediation changed after the operator loaded it and returns a stale-state business error. Remediation and recovery take the same case-row lock. Recovery rechecks `PROCESSED`/`CLOSED` and recalculates the latest `FAILED` disposal under that lock before compare-and-set; a superseded requested record is stale. The browser refreshes the case before another action.

## DR-124-004: Complaint Reference Choices Expose Minimal Labels

The complaint module returns only the stable ID and display label needed to choose a tenant, channel, signature, or template. Signature and template choices also expose their owning tenant ID; channels do not claim a tenant relation that their schema does not contain. The form submits stable IDs. Empty selection means unknown attribution; the UI never guesses the first option.

## DR-124-005: Reuse the Phase 41 Route and Modal Semantics

The amendment keeps `/admin/complaints` and Phase 41 visual tokens. The contextual dialog uses the existing focus/ARIA semantics inside a real full-viewport backdrop that blocks page interaction. Pending work blocks backdrop click, close, cancel, and Escape; a synchronous latch blocks duplicate submission and is released after failure. No new route or component library is introduced.

## DR-124-006: Return Stable Complaint Failure Codes

Complaint mutation failures use the shared exception boundary with an HTTP status and `data.errorCode`; security-audit logging therefore remains consistent with other console endpoints. Stale state is HTTP 409 with `COMPLAINT_STATE_STALE`; not-found remains distinct. The browser refreshes only for the stale code instead of treating every validation failure as concurrency.

## DR-124-007: Failed Remediation Evidence Commits in the Locking Command Transaction

A resource mutation failure may mark its nested Spring transaction rollback-only even when the complaint service catches the exception. The resource transaction therefore exits before failure evidence is written. The enclosing command transaction still owns the complaint-row lock and records the `FAILED` disposal row and matching `REMEDIATION_FAILED` event atomically; the success path keeps the resource change, `APPLIED` disposal row, and event in the nested resource transaction. A Spring transaction integration test, not only a mocked service test, proves the failure evidence survives a real rollback-only resource failure without releasing the case lock.

## DR-124-008: Freeze Action Eligibility From Persisted State

`PENDING` permits accept, `PROCESSING` permits handle, and `PROCESSED` permits remediation or close. A recovery targets the exact latest `FAILED` disposal record and remains available in `PROCESSED` or `CLOSED`; it is a compensation record and does not reopen the case. `FINANCE` is read-only. The server enforces these rules even when a client forges a request.

Acceptance opinion is required. This guarantees that every newly accepted case has durable acceptance evidence; the backend and dialog apply the same validation.

## DR-124-009: Freeze Mutations Across Event-Writer Cutover

V6600 is additive for readers but not for complaint writers. Before applying V6600, operators disable complaint registration and every complaint mutation. Mutations remain disabled while the migration/backfill runs and until every active application instance writes case events. The same freeze applies to any downgrade window. Read-only access may continue. This prevents both old writers and partially rolled fleets from creating an unrecoverable timeline gap.
