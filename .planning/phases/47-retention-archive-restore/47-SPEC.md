# Phase 47 SPEC — Retention archive and restore

Package: `retention-archive-restore`

Goal: eligible hot data is captured into encrypted, checksummed archive manifests and can be verified, restored, and exported without losing source identity.

Owned obligations:

- OBL-NFR-RETENTION-TWO-YEAR
- OBL-NFR-HOT-COLD
- OBL-NFR-ARCHIVE-ENCRYPT
- OBL-NFR-ARCHIVE-RESTORE
- OBL-DOD-06-DATA

Scope:

- Policy-driven two-year retention defaults for core evidence domains.
- Hot-to-cold scan against whitelisted source tables.
- Encrypted archive manifest with checksum, source identities, retention/legal-hold/deletion state and failure reason.
- Authorized verify, restore and archive export actions.

Out of scope:

- Physical object-store provisioning and physical MySQL partition management.
- Production HA or disaster-recovery topology.
