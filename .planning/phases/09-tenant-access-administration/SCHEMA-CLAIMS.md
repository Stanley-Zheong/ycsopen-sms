# Schema Claims

| Claim ID | Schema object/prefix | Owner package | Migration ID | Depends on migration | Compatibility step | Rollback | Cross-owner approval |
| --- | --- | --- | --- | --- | --- | --- | --- |
| SC-09-001 | ycs.sms.tenant-access-administration.tenant_api_keys | tenant-access-administration | V1800 | V1700,V1701 | expand | Forward-compatible disable or restored snapshot; retain old columns and rows | - |
| SC-09-002 | ycs.sms.tenant-access-administration.tenant_protocol_credentials | tenant-access-administration | V1801 | V1800 | expand | Forward-compatible disable or restored snapshot; retain old columns and rows | - |
