# Phase 46 SCHEMA CLAIMS

| Claim ID | Schema object/prefix | Owner package | Migration ID | Required columns/indexes | Compatibility notes |
|---|---|---|---|---|---|
| SCHEMA-P46-EXPORT-TASKS | export_tasks | secure-async-export | V5500__secure_async_export.sql | request_id, tenant_id, producer, job_name, authorization_snapshot, source_snapshot, artifact_manifest, artifact_ciphertext, file_sha256, encryption_state, retry_count, split_count, partial_failure_count, failure_reason, download_token_hash, expires_at, completed_at, updated_at | Extends existing table and preserves legacy columns |
| SCHEMA-P46-PERMISSIONS | permissions | secure-async-export | V5500__secure_async_export.sql | secure-async-export:menu/read/create/download/retry | Uses existing permission_code schema |
