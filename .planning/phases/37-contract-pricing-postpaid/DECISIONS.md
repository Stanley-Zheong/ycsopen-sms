# Phase 37 Decisions

- Use one active contract per tenant for this phase; historical versioning can be added by a later contract-amendment phase.
- Seed a minimal `SMS_STANDARD_V1` price book version so conversion can reference an immutable version.
- Keep the UI inside existing trial contract and tenant overview pages instead of creating extra pages.
- Validate postpaid credit synchronously and fail closed when the ceiling is exceeded.
- Leave reconciliation, settlement, invoice, and fee-warning behavior to their owned later phases.
- Reuse persisted tenant/account/trial/message/complaint/price sources for the
  workbench; introduce no analytics shadow table.
- Treat `trial_accounts` as authoritative while supporting legacy tenant trial
  columns during convergence; provision new approvals into the authoritative
  table.
- Require VERIFIED tenant and NORMAL account plus eligible lifecycle/trial,
  absent contract, and ACTIVE selected price under lock.
- Derive configuration traceability from persisted content, never the
  optimistic-lock revision or browser input.
