# Spirit 02 System Design

## Page Ownership

Admin operations pages own business API adapters and row eligibility rules. Shared Spirit 01 components own confirmation mechanics, query layout, table states, and form behavior.

## Data Flow

Operations data flows from list APIs into row models with explicit eligibility and next-action metadata. Complaint rows are joined in the page with persisted remediation records so recovery is derived from server-owned `FAILED` state rather than transient page state. Unknown or incomplete attribution remains a distinct UI state. Complaint analytics includes an ordered daily trend derived from stored case creation dates.

## Command Flow

1. User chooses a row or scoped page action.
2. Page builds an action descriptor containing action name, target, effect, required input, and API adapter.
3. Shared confirmation captures reason or approval input.
4. Page submits one request, refreshes data, and records success or error feedback.

For complaint recovery, the page first resolves the complaint's latest persisted
failed remediation. A successful or already recovered record never produces a
recoverable command.

## Failure Model

Business rejection stays in the modal or row context. A failed complaint remediation displays its stored failure reason and recovery action. System failure releases retry only after the first request has completed or failed.

## Audit Expectations

All status changes include actor, target, reason when required, request trace, and result in the backend or documented verification boundary.
