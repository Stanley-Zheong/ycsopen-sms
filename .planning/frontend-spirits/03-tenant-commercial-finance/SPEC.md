# Spirit 03: Tenant Commercial And Finance Spec

## Intent

Make tenant lifecycle, trial, contract, recharge, billing, account balance, and finance views coherent across sales, finance, operations, and customer users.

## Scope

### In

- Trial quota, formal contract, pricing version, prepaid balance, postpaid credit, recharge review, billing summary, invoice and reconciliation UI semantics.
- Customer-visible commercial state and finance-facing operational state.
- Finance table/query layout follow-up from issue `#79` and PRD V2 finance TODOs.
- Issue `#122` trial-conversion workbench: source-backed tenant discovery,
  trial-period analysis, server-owned eligibility, and tenant-bound contract
  confirmation.

### Out

- Provider routing internals except cost and price snapshots needed for display.
- Non-finance operations pages.

## Behavior

| Behavior ID | Required behavior | Observable acceptance |
|---|---|---|
| FE-SPIRIT-03-LIFECYCLE | Tenant lifecycle displays trial, signed, frozen, terminated, and transition eligibility with clear next actions. | Users see current state, blocked reason, next action, and required actor. |
| FE-SPIRIT-03-LEDGER | Finance views distinguish trial quota, prepaid balance, postpaid credit, billed amount, unsettled amount, and invoice status. | Query/result/export state uses clear labels and data source notes. |
| FE-SPIRIT-03-SNAPSHOT | Billing and export pages describe price version and snapshot behavior when available, or explicitly state the verification boundary. | Generated financial views do not imply live recalculation when snapshot semantics apply. |
| FE-SPIRIT-03-TRIAL-CONVERSION | An authorized platform operator discovers persisted trial tenants, reviews one tenant's source-backed trial analysis, and confirms conversion against that selected tenant only. | Filters alter the query; loading, empty, error, quality, eligibility, and price states are explicit; the modal repeats immutable tenant identity; rejection preserves input and success refreshes the workbench. |

## Remaining TODO

- Open item: Confirm zero-amount trial billing behavior from product review.
- Selected route: `/admin/tenant-trial-contracts`, canonical Phase 37 behaviors
  `contract-pricing-postpaid-02`, `contract-pricing-postpaid-04`, and
  `contract-pricing-postpaid-05`.
- Open item: Record issue `#122` final evidence in `QUALITY-GATEWAY.md`.
