# Spirit 03 System Design

## Data Flow

Commercial state flows from tenant lifecycle, account, recharge, billing, invoice, and contract APIs into separate view models. Derived labels must preserve the source concept: trial quota, prepaid balance, postpaid credit, billed amount, unsettled amount, or invoice status.

For issue `#122`, the workbench reads tenant/account/trial state and aggregates
messages and complaints only inside the persisted half-open trial window. It
exposes source, latest timestamp, quality, and a content-addressed trial
configuration snapshot. Analysis uses the same selected tenant/window.

## Command Flow

Finance actions such as recharge approval, credit adjustment, billing confirmation, and invoice processing use contextual confirmation and record target, amount, reason, and resulting state.

Trial adjustment and conversion open from a concrete row and carry no editable
tenant identity. Conversion selects an active price book, then the server locks
tenant/account, trial, and selected price, revalidates eligibility, inserts the
contract, and changes both lifecycle records in one transaction.

## UI Model

Finance query panels use shared QueryPanel. Summary cards must show data source and freshness when values are derived or aggregated.

The trial workbench uses QueryPanel plus a stable result table. Shared dialogs
host analysis, trial adjustment, and conversion. Stable selectors cover query,
table/empty, row actions, entity form, submit, cancel, and existing contract
fields.

## Failure Model

Validation errors remain on amount/date/contract fields. Business rejections explain why the commercial state cannot transition.

List, analysis, and price queries expose explicit loading/error/empty/no-data
states. A rejected conversion keeps every field and releases the submit latch
for an explicit retry.

## Verification Model

Unit tests cover view model labeling and state eligibility. Chrome Playwright covers one finance query, one state-changing finance action, and one empty or error state per selected implementation issue.

Issue `#122` adds focused service tests for source windows, compatibility,
eligibility, active pricing, atomic transition, rollback, authorization, and
audit; component and Chromium tests cover the selected-tenant UI flow.
