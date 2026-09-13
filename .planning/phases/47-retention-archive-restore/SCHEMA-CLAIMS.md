# Phase 47 SCHEMA CLAIMS

| Claim ID | Schema object/prefix | Owner package | Migration ID | Depends on migration | Compatibility step | Rollback | Cross-owner approval |
| --- | --- | --- | --- | --- | --- | --- | --- |
| SC-47-001 | ycs.sms.retention-archive-restore.archive_policies | retention-archive-restore | V5600__retention_archive_restore.sql | V5500__secure_async_export.sql | expand | rollback=forward-compatible-disable-policy-or-restored-snapshot | - |
| SC-47-002 | ycs.sms.retention-archive-restore.archive_manifests | retention-archive-restore | V5600__retention_archive_restore.sql | V5500__secure_async_export.sql | expand | rollback=forward-compatible-disable-policy-or-restored-snapshot | - |
| SC-47-003 | ycs.sms.retention-archive-restore.archive_restore_jobs | retention-archive-restore | V5600__retention_archive_restore.sql | V5500__secure_async_export.sql | expand | rollback=forward-compatible-disable-policy-or-restored-snapshot | - |
