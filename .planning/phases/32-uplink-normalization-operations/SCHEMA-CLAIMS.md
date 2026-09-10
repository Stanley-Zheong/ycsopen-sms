# Phase 32 Schema Claims

| Claim ID | Schema object/prefix | Owner package | Migration ID | Depends on migration | Compatibility step | Rollback | Cross-owner approval |
| --- | --- | --- | --- | --- | --- | --- | --- |
| SC-32-001 | ycs.sms.uplink-normalization-operations.* | uplink-normalization-operations | V4100 | V3700 | expand | forward-fix by adding nullable columns or compensating migration; no destructive rollback | - |
