# Spirit 03 System Design

## Data Flow

Commercial state flows from tenant lifecycle, account, recharge, billing, invoice, and contract APIs into separate view models. Derived labels must preserve the source concept: trial quota, prepaid balance, postpaid credit, billed amount, unsettled amount, or invoice status.

## Command Flow

Finance actions such as recharge approval, credit adjustment, billing confirmation, and invoice processing use contextual confirmation and record target, amount, reason, and resulting state.

## UI Model

Finance query panels use shared QueryPanel. Summary cards must show data source and freshness when values are derived or aggregated.

## Failure Model

Validation errors remain on amount/date/contract fields. Business rejections explain why the commercial state cannot transition.

## Verification Model

Unit tests cover view model labeling and state eligibility. Chrome Playwright covers one finance query, one state-changing finance action, and one empty or error state per selected implementation issue.
