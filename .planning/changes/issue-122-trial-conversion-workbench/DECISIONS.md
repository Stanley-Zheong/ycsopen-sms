# Issue 122 Decisions

## Reuse domain sources

Tenant identity, sales owner, industry, and lifecycle come from `tenants`;
account eligibility comes from `tenant_accounts`; authoritative trial state
comes from `trial_accounts` with legacy `tenants.trial_*` fields used only as a
compatibility fallback; performance and complaints come from `message_tasks`
and `complaints`; valid pricing comes from `tenant_price_books`.

Newly approved tenants are provisioned into `trial_accounts` by
`TenantReviewService`; trial adjustment keeps both the authoritative row and
legacy compatibility columns synchronized. No duplicate analytics persistence
is introduced.

## One conversion rule

Conversion requires a VERIFIED tenant, NORMAL tenant account, eligible
trial/lifecycle state, no existing contract, and an ACTIVE submitted price
book. The list projects this rule and approval repeats it under lock in the
order tenant/account, trial, then selected price book.

## Preserve existing schema

Schema migrations: none. Existing Phase 8, 22, 34, 37, and 41 tables contain
all required data. Configuration traceability is
`TRIAL-SNAPSHOT-V1-<SHA-256 prefix>` derived from exact quota/start/end values.
