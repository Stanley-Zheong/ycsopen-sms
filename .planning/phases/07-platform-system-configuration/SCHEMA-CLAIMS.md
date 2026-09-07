# Schema Claims

| Claim ID | Schema object/prefix | Owner package | Migration ID | Depends on migration | Compatibility step | Rollback | Cross-owner approval |
| --- | --- | --- | --- | --- | --- | --- | --- |
| SC-07-001 | ycs.sms.platform-system-configuration.versions-state | platform-system-configuration | V1600 | V1501 | expand | rollback by disabling configuration mutations and applying a forward corrective migration while preserving immutable versions | - |
| SC-07-002 | ycs.sms.platform-system-configuration.permissions | platform-system-configuration | V1601 | V1600 | expand | rollback by disabling Phase 07 permission rows before a forward corrective migration | - |
