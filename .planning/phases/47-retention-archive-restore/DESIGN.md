# Phase 47 DESIGN

Schema migrations: declared.

Core design:

- `archive_policies` stores data-domain policy, two-year default retention, hot-month cutoff, legal hold and status.
- `archive_manifests` stores source identity, partition key, row count, AES/GCM ciphertext, checksum, key version, retention date, legal hold, deletion eligibility, verification and restore/export state.
- `archive_restore_jobs` stores restore/export attempts and result evidence.
- `RetentionArchiveService` uses a fixed domain-to-query switch for hot-data scans.
- Archive export reuses Phase 46 `SecureAsyncExportService` with export type `ARCHIVE_RESTORE`.

Failure handling:

- Forced archive failure records `FAILED` and reason.
- Decryption/checksum failure marks a manifest `CORRUPTED`.
- Restore/export refuses corrupted or failed manifests and records a failed job.
