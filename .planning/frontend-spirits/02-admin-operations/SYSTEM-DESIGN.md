# Spirit 02 System Design

## Page Ownership

Admin operations pages own business API adapters and row eligibility rules. Shared Spirit 01 components own confirmation mechanics, query layout, table states, and form behavior.

## Data Flow

Operations data flows from list APIs into row models with explicit eligibility and next-action metadata. Complaint rows are joined in the page with persisted remediation records so recovery is derived from server-owned `FAILED` state rather than transient page state. Unknown or incomplete attribution remains a distinct UI state. Complaint analytics includes an ordered daily trend derived from stored case creation dates.

Issue `#124` adds a selected-case read model. The list remains the navigation surface; selecting a row loads the case snapshot and complete timeline. Intake reference choices come from a minimal complaint-owned option endpoint and submit stable IDs without guessing a default.

## Command Flow

1. User opens one complaint row; the page loads its server-owned single-case detail and timeline.
2. User chooses an eligible action in that workspace. The page creates an empty action draft and an action descriptor containing case, current state, effect, required input, and API adapter.
3. Shared confirmation displays the target context and captures only that stage's evidence.
4. A synchronous latch submits one request. The page refreshes the list and selected-case detail after success or stale-state rejection.

For complaint recovery, the loaded detail identifies the latest persisted failed
remediation. The server locks the complaint and revalidates that exact record is
still latest before update. A successful, superseded, or already recovered record
never produces a recoverable command.

## Failure Model

Business rejection stays in the modal or row context. A failed complaint remediation displays its stored failure reason and recovery action. System failure releases retry only after the first request has completed or failed.

List, reference-choice, and selected-case reads have independent loading, error, empty, and retry states. Compare-and-set state rejection cannot overwrite a concurrent transition.

## Audit Expectations

All status changes include actor, target, reason when required, request trace, and result in the backend or documented verification boundary.
