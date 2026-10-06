# Issue 121 Schema Claims

| Claim ID | Schema object/prefix | Owner package | Migration ID | Depends on migration | Compatibility step | Rollback | Cross-owner approval |
| --- | --- | --- | --- | --- | --- | --- | --- |
| SC-C121-001 | `ycs.sms.privileged-data-access-audit.privileged_operation_audits.tenant-resource-index` / `idx_audit_tenant_resource_id` | issue-121-tenant-onboarding-access | V6800 | V1500,V1800,V6700 | expand; add the non-unique tenant/resource/id index while old and new readers keep the same query and writers keep the same row shape | A downgrade keeps the additive index; after tenant audit reader removal, `DROP INDEX idx_audit_tenant_resource_id ON privileged_operation_audits` compensates the expansion. | DR-C121-001,DR-C121-003 |
