# Phase 37 Schema Claims

- `tenant_price_books.price_book_version` is unique and referenced by contracts.
- `tenant_contracts.tenant_id` is unique for this phase's one-effective-contract model.
- POSTPAID contracts store positive `credit_limit_mil` and billing period.
- `postpaid_usage_ledger.business_doc_id` is unique for idempotent usage recording.
- Period usage is summed by tenant and effective period before accepting new postpaid usage.
