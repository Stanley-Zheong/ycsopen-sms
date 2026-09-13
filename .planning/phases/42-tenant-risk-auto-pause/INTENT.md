# Phase 42 Intent

The implementation is intentionally small:

- Store tenant risk episodes in one table.
- Use `tenant_alert_rules` for tenant-facing risk thresholds.
- Reuse `alert_records` only as alert evidence.
- Reuse the existing tenant lifecycle `FROZEN` fence to block new work.
- Keep read/reconciliation behavior unchanged.

The important product behavior is correctness under incomplete source data: a zero denominator or missing denominator must never render as `0%` or "safe".
