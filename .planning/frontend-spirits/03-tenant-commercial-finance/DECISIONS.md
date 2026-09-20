# Spirit 03 Decisions

## DR-FE03-001: Commercial State Is Not A Single Balance

### Status
Accepted

### Context
PRD V2 separates trial quota, prepaid balance, postpaid credit, contract pricing, and billing snapshots.

### Decision
Frontend finance views must render these as separate concepts with labels and source notes. A single “余额” display is insufficient when multiple commercial controls affect sending eligibility.

### Consequences

- Finance and tenant pages need explicit terminology alignment.
- Tests must assert the correct label, not only numeric rendering.
