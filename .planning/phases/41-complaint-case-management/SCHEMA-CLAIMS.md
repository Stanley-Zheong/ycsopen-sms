# Phase 41 Schema Claims

| Claim ID | Migration | Objects | Purpose | Verification |
|---|---|---|---|---|
| SCHEMA-P41-01 | V5000__complaint_case_management.sql | complaints | Add complaint content type, mobile, attribution quality, requirement, opinion, remediation, actor and state timestamps. | ComplaintCaseManagementMigrationTest |
| SCHEMA-P41-02 | V5000__complaint_case_management.sql | disposal_records | Add idempotency key, authorized review, status, failure reason, and original complaint reference. | ComplaintCaseServiceTest |
| SCHEMA-P41-03 | V5000__complaint_case_management.sql | tenants/channels/signatures/templates | Reuse existing lifecycle columns for exact remediation target effects. | ComplaintCaseServiceTest |
| SCHEMA-P41-04 | V1__init_schema.sql → V5000__complaint_case_management.sql | complaints/disposal_records | V1 already defines both tables; V5000 extends that schema and uses standalone `CREATE TABLE IF NOT EXISTS` bootstrap only so isolated H2 migration tests can execute V5000 directly. | `rg "CREATE TABLE complaints|CREATE TABLE disposal_records" core/src/main/resources/db/migration/V1__init_schema.sql` |
