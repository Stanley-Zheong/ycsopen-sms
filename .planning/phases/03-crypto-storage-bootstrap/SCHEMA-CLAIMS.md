# Schema Claims

| Claim ID | Schema object/prefix | Owner package | Migration ID | Depends on migration | Compatibility step | Rollback | Cross-owner approval |
| --- | --- | --- | --- | --- | --- | --- | --- |
| SC-03-001 | ycs.sms.crypto-storage-bootstrap.* | crypto-storage-bootstrap | V1200 | V1 | expand | Revert application readers/writers to the previous compatible path while leaving V1200 additive objects intact; if migrated data requires recovery, stop new claims and restore the preflight-verified encrypted snapshot, then resume with a forward-fix migration. | - |

V1200 is an umbrella claim for Phase-owned metadata only. It does not claim or modify legacy V1 DDL.

## V1200 physical object claim

All physical objects use the registered `ycs_crypto_` prefix and belong to
`SCHEMA-P03`. V1200 creates exactly these tables:

| Table | Contract |
| --- | --- |
| `ycs_crypto_key_references` | Purpose/version key references, constrained lifecycle, bounded KEK wrap count, rotation flag and optimistic version; no key material. |
| `ycs_crypto_migration_targets` | Seven reviewed targets, including both protected no-index mobile columns and the schema-only risk-log digest. |
| `ycs_crypto_blind_indexes` | One 53-character ASCII-bin HMAC index per target/row/field/key version, with exact lookup, uniqueness, key-reference and reconciliation digests. |
| `ycs_crypto_manifest_pair_admission` | The single global writer/snapshot admission tuple and unsigned replay sequence. Both role digests are non-null in the same row. |
| `ycs_crypto_migration_runs` | Pair-bound run state, lease identity, safe aggregate counters and optimistic version. |
| `ycs_crypto_migration_checkpoints` | Per-target state, paired cursor/digest, lease and safe aggregate counters. |
| `ycs_crypto_migration_events` | Allowlisted categories, outcomes, digested locators and counts only. |
| `ycs_crypto_registration_sessions` | OPEN/CLAIMED/CLOSED/EXPIRED session, tenant-draft binding, versioned upload-credential digest, expiry and session attempt ceiling. |
| `ycs_crypto_registration_upload_attempts` | Per-session/per-purpose admitted attempt ceiling. |
| `ycs_crypto_protected_objects` | Purpose-bound staged object, replacement relation, one current object per purpose, single claim, expiry and opaque store locator. |
| `ycs_crypto_object_capabilities` | Object/tenant/subject/purpose-bound versioned capability digest, state and expiry. |
| `ycs_crypto_object_operations` | Reserved/store/metadata/reconciliation operation state without payload or provider diagnostics. |

The migration also inserts the seven reviewed target declarations into
`ycs_crypto_migration_targets`. It creates no trigger, routine, view or legacy
object and requires no `SUPER` privilege. Runtime mutations use the declared
optimistic versions, state predicates and ceilings in one transaction; exact
constraints, foreign keys and unique indexes reject invalid stored shapes.

V1201 is the next available migration in the registered V1200-V1299 namespace.
