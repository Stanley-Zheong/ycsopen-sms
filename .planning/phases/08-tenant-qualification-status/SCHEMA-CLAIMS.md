# Schema Claims

| Claim ID | Schema object/prefix | Owner package | Migration ID | Depends on migration | Compatibility step | Rollback | Cross-owner approval |
| --- | --- | --- | --- | --- | --- | --- | --- |
| SC-08-001 | ycs.sms.tenant-qualification-status.tenant-challenge-events | tenant-qualification-status | V1700 | V1501,V1601 | expand | disable Phase 08 mutations and use a forward corrective migration while retaining safe history | DR-08-002, DR-08-007 |
| SC-08-002 | ycs.sms.tenant-qualification-status.permissions | tenant-qualification-status | V1701 | V1700 | expand | disable Phase 08 grants before a forward corrective migration | DR-08-004 |

The existing `tenants`, `tenant_accounts`, and `users` rows are updated because Phase 08 owns their qualification, initial-access, and operating-state facts. The legal-ID protected-object size constants are corrected under DR-08-005; no Phase 03 table or encryption format changes. Later signature, template, and ingress owners consume `TenantEligibilityPolicy` rather than duplicate status facts.
