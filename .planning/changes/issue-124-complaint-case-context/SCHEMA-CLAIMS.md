# Issue 124 Schema Claims

| Claim ID | Schema object/prefix | Owner package | Migration ID | Depends on migration | Compatibility step | Rollback | Cross-owner approval |
| --- | --- | --- | --- | --- | --- | --- | --- |
| SC-C124-001 | `ycs.sms.complaint-case-management.complaint_case_events` / `complaint_case_events` | issue-124-complaint-case-context | V6600 | V5000 | expand; disable complaint registration and mutations before migration, keep them disabled through backfill and mixed-version rollout, and resume only after every active instance writes events; apply the same freeze for downgrade | Application downgrade keeps the additive table and read access; after event export and reader removal, `DROP TABLE complaint_case_events` compensates the expansion. Mutation traffic resumes only when all active instances are event-writing. | - |
