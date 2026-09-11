# Phase 47 DECISIONS

- DR-P47-001: Use an in-database encrypted archive manifest for this phase rather than provisioning a separate object store. Reason: this preserves encrypted/checksummed/archive/restore semantics while avoiding infrastructure expansion.
- DR-P47-002: Hot-data scans use a code-owned whitelist of known source tables and fields. Client-provided table names or SQL are not accepted.
- DR-P47-003: Physical partition DDL is represented as policy metadata and indexes in this phase. Production partition mechanics belong to deployment/performance assurance, not this product slice.
- DR-P47-004: Archive ciphertext is not serialized by the console API. Verification/decrypt helpers remain service-internal so the list surface does not expose payload bytes.
- DR-P47-005: Permission metadata uses one method-neutral `retention-archive:write` authority for both policy PUT and scan/archive POST operations instead of splitting low-value button-level codes.
