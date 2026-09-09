# Schema Claims

| Claim ID | Schema object/prefix | Owner package | Migration ID | Depends on migration | Compatibility step | Rollback | Cross-owner approval |
| --- | --- | --- | --- | --- | --- | --- | --- |
| SC-06-001 | ycs.sms.privileged-data-access-audit.operation-audit | privileged-data-access-audit | V1500 | V1402 | expand | rollback by disabling the Phase 06 writer and applying a forward corrective migration while preserving immutable rows | - |
| SC-06-002 | ycs.sms.privileged-data-access-audit.permissions | privileged-data-access-audit | V1501 | V1500 | expand | rollback by disabling Phase 06 permission rows before a forward corrective migration | - |
