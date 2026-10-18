# Issue 122 System Design

## Ownership

| Rule | Owner |
|---|---|
| Candidate, analysis, pricing, eligibility, and contract transaction | `ContractPricingService` |
| Initial trial provisioning | `TenantReviewService` |
| Trial adjustment and compatibility synchronization | `TrialPrepaidLedgerService` |
| Role/permission/scope boundary | `ContractPricingController` |
| Query, dialogs, validation, and feedback | `TrialPrepaidAdminPage` |
| Authenticated HTTP result audit | Existing `OperationAuditInterceptor` |

## Data and state flow

`GET workbench -> filter/select -> GET analysis -> open conversion -> validate
fields -> POST selected tenant -> lock tenant/account -> lock trial -> lock
price -> revalidate -> insert contract -> trial CONTRACTED -> tenant SIGNED ->
commit -> refresh workbench`.

Any validation, lifecycle, duplicate-contract, price-book, or persistence
failure rolls back business state. The modal retains its fields and shows the
server reason.

Metrics use the half-open persisted trial interval `[start_at,end_at)`. Ratios
use message count as denominator and are null at zero. Responses report source
registry, latest contributing time, and `COMPLETE`, `INCOMPLETE`, or `NO_DATA`.

## UI states

The workbench defines loading, empty, error/retry, eligible, and ineligible
states. Analysis defines loading, error, no-data, and populated states. The
price selector defines loading, error, empty, and populated states. Pending
mutation disables confirm/cancel and success refreshes the candidate query.
