---
name: test-fixture-plan
description: "Create a mutation-free fixture strategy for an approved ycsopen-sms automation contract, including accounts, tenants, roles, SMS tasks, channels, billing states, object ownership, setup/readback, isolation, cleanup, and environment dependencies. Do not perform writes."
---

# Fixture plan

Write the plan under the active phase `EVIDENCE/fixtures/`:

```yaml
case_id:
strategy: reuse-readonly | api-create | migration-seed | local-db-seed | external-sandbox | impossible
target_environment:
account_alias:
objects: []
setup_steps: []
readback_checks: []
ownership:
isolation:
cleanup_restore: []
secret_names: []
```

Mark each object `existing-readonly`, `owned-copy`, or `new-this-run`. Exact IDs
come from readback or creation output, never plausible constants. Shared users,
tenants, API keys, channels, phone numbers, queues, config, and cleanup must be
isolated by per-worker resources, unique prefixes, separate contexts/accounts,
or an explicitly justified scoped lock. Secret names may be recorded; values
may not.

Choose `impossible` when no controlled fixture or objective oracle exists. A
plan that requires writes goes to `test-fixture-provision`; verified read-only
reuse can go directly to spec generation.
