# Phase 40 Schema Claims

- `V4900__fee_warning_credit_enforcement.sql` creates `fee_warning_rules`.
- `V4900__fee_warning_credit_enforcement.sql` creates `fee_warning_episodes`.
- Phase40 reads `prepaid_accounts`, `tenant_contracts`, and `postpaid_usage_ledger`.
- Phase40 writes notification evidence to `alert_records` and `alert_delivery_attempts`.
