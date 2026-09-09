# Schema Claims

| Claim ID | Schema object/prefix | Owner package | Migration ID | Depends on migration | Compatibility step | Rollback | Cross-owner approval |
| --- | --- | --- | --- | --- | --- | --- | --- |
| SC-05-001 | ycs.sms.console-identity-platform-rbac.sessions-history-outbox | console-identity-platform-rbac | V1400 | V1 | expand | rollback by retaining additive nullable columns and tables, then apply a forward corrective migration after snapshot verification | - |
| SC-05-002 | ycs.sms.console-identity-platform-rbac.permission-seed | console-identity-platform-rbac | V1401 | V1400 | expand | rollback by disabling Phase 5 permission rows before a forward corrective migration | - |
