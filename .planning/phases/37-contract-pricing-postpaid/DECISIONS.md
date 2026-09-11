# Phase 37 Decisions

- Use one active contract per tenant for this phase; historical versioning can be added by a later contract-amendment phase.
- Seed a minimal `SMS_STANDARD_V1` price book version so conversion can reference an immutable version.
- Keep the UI inside existing trial contract and tenant overview pages instead of creating extra pages.
- Validate postpaid credit synchronously and fail closed when the ceiling is exceeded.
- Leave reconciliation, settlement, invoice, and fee-warning behavior to their owned later phases.
