# Phase 49 Schema Claims

| Claim ID | Schema object/prefix | Owner package | Migration ID | Depends on migration | Compatibility step | Rollback | Cross-owner approval |
| --- | --- | --- | --- | --- | --- | --- | --- |
| P49-SCHEMA-REQUESTS | tenant_termination_requests | tenant-cooperation-termination | V5800 | V1 tenants | Additive create table | Drop table before production data | Not required; new owner table |
| P49-SCHEMA-PARTICIPANTS | tenant_termination_participants | tenant-cooperation-termination | V5800 | V5800 requests | Additive create table | Drop table before production data | Not required; new owner table |
| P49-SCHEMA-AUDITS | tenant_termination_audits | tenant-cooperation-termination | V5800 | V5800 requests | Additive create table | Drop table before production data | Not required; new owner table |
| P49-SCHEMA-PERMISSIONS | permissions tenant-termination:* | tenant-cooperation-termination | V5800 | V1 permissions | Idempotent insert by permission_code | Delete tenant-termination:* permissions | Uses existing permission_code schema |
