-- Phase 03 expand-only metadata. Legacy V1 objects are deliberately untouched.

CREATE TABLE ycs_crypto_key_references (
    purpose VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    key_version BIGINT UNSIGNED NOT NULL,
    provider_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_key_reference VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    key_state VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    wrap_operation_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    rotation_required BOOLEAN NOT NULL DEFAULT FALSE,
    optimistic_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (purpose, key_version),
    UNIQUE KEY uk_ycs_crypto_key_provider_reference (purpose, provider_id, provider_key_reference),
    CONSTRAINT chk_ycs_crypto_key_purpose CHECK (purpose IN (
        'FIELD_ENCRYPTION_KEK',
        'MOBILE_BLIND_INDEX',
        'OBJECT_CAPABILITY_DIGEST',
        'REGISTRATION_UPLOAD_DIGEST',
        'SNAPSHOT_RECOVERY'
    )),
    CONSTRAINT chk_ycs_crypto_key_state CHECK (key_state IN (
        'PREPARED', 'ACTIVE', 'ROTATION_REQUIRED', 'DECRYPT_ONLY',
        'RETIRING', 'RETIRED', 'COMPROMISED'
    )),
    CONSTRAINT chk_ycs_crypto_wrap_ceiling CHECK (wrap_operation_count <= 1048576),
    CONSTRAINT chk_ycs_crypto_wrap_purpose CHECK (
        (purpose IN ('FIELD_ENCRYPTION_KEK', 'SNAPSHOT_RECOVERY')
            AND rotation_required = (wrap_operation_count >= 983040)
            AND (rotation_required = FALSE OR key_state IN (
                'ROTATION_REQUIRED', 'DECRYPT_ONLY', 'RETIRED', 'COMPROMISED'
            )))
        OR
        (purpose NOT IN ('FIELD_ENCRYPTION_KEK', 'SNAPSHOT_RECOVERY')
            AND wrap_operation_count = 0
            AND rotation_required = FALSE)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ycs_crypto_migration_targets (
    target_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    legacy_table_name VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    legacy_column_name VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    target_disposition VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    target_state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DISCOVERED',
    legacy_fallback_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    optimistic_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (target_type),
    UNIQUE KEY uk_ycs_crypto_target_legacy_column (legacy_table_name, legacy_column_name),
    CONSTRAINT chk_ycs_crypto_target_disposition CHECK (target_disposition IN (
        'BLIND_INDEX', 'MIGRATABLE_SCHEMA_ONLY', 'PROTECTED_NO_INDEX'
    )),
    CONSTRAINT chk_ycs_crypto_target_state CHECK (target_state IN (
        'DISCOVERED', 'BACKFILLED', 'VERIFIED', 'CUTOVER', 'SCRUBBED', 'COMPLETE'
    )),
    CONSTRAINT chk_ycs_crypto_target_fallback CHECK (
        (target_disposition = 'PROTECTED_NO_INDEX' AND legacy_fallback_allowed = FALSE)
        OR target_disposition <> 'PROTECTED_NO_INDEX'
    ),
    CONSTRAINT chk_ycs_crypto_complete_fallback CHECK (
        target_state <> 'COMPLETE' OR legacy_fallback_allowed = FALSE
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO ycs_crypto_migration_targets
    (target_type, legacy_table_name, legacy_column_name, target_disposition, legacy_fallback_allowed)
VALUES
    ('MOBILE_PORTABILITY', 'mobile_portability', 'mobile_hash', 'BLIND_INDEX', TRUE),
    ('BLACKLIST_ENTRY', 'blacklist_entries', 'mobile_hash', 'BLIND_INDEX', TRUE),
    ('THIRD_PARTY_RISK_CHECK_LOG', 'third_party_risk_check_logs', 'mobile_hash', 'MIGRATABLE_SCHEMA_ONLY', TRUE),
    ('MESSAGE_TASK', 'message_tasks', 'mobile_hash', 'BLIND_INDEX', TRUE),
    ('UNSUBSCRIBE_RECORD', 'unsubscribe_records', 'mobile_hash', 'BLIND_INDEX', TRUE),
    ('BULK_SENDING_ITEM_MOBILE', 'bulk_sending_items', 'mobile_encrypted', 'PROTECTED_NO_INDEX', FALSE),
    ('UPLINK_RECORD_MOBILE', 'uplink_records', 'mobile_encrypted', 'PROTECTED_NO_INDEX', FALSE);

CREATE TABLE ycs_crypto_blind_indexes (
    blind_index_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    target_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    legacy_row_id BIGINT UNSIGNED NOT NULL,
    field_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    key_purpose VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'MOBILE_BLIND_INDEX',
    key_version BIGINT UNSIGNED NOT NULL,
    index_value CHAR(53) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    index_status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    original_row_digest BINARY(32) NOT NULL,
    optimistic_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (blind_index_id),
    UNIQUE KEY uk_ycs_crypto_blind_target_version
        (target_type, legacy_row_id, field_id, key_version),
    KEY idx_ycs_crypto_blind_lookup
        (target_type, field_id, index_status, key_version, index_value),
    CONSTRAINT fk_ycs_crypto_blind_target FOREIGN KEY (target_type)
        REFERENCES ycs_crypto_migration_targets (target_type),
    CONSTRAINT fk_ycs_crypto_blind_key FOREIGN KEY (key_purpose, key_version)
        REFERENCES ycs_crypto_key_references (purpose, key_version),
    CONSTRAINT chk_ycs_crypto_blind_purpose CHECK (key_purpose = 'MOBILE_BLIND_INDEX'),
    CONSTRAINT chk_ycs_crypto_blind_value CHECK (
        CHAR_LENGTH(index_value) = 53 AND index_value REGEXP '^[a-z2-7]{53}$'
    ),
    CONSTRAINT chk_ycs_crypto_blind_status CHECK (index_status IN ('ACTIVE', 'RETIRING'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ycs_crypto_manifest_pair_admission (
    singleton_id TINYINT UNSIGNED NOT NULL,
    migration_set_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    canonical_subject_digest BINARY(32) NOT NULL,
    global_sequence BIGINT UNSIGNED NOT NULL,
    signer_key_version VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    signer_fingerprint BINARY(32) NOT NULL,
    writer_digest BINARY(32) NOT NULL,
    snapshot_digest BINARY(32) NOT NULL,
    pair_digest BINARY(32) NOT NULL,
    optimistic_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    admitted_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (singleton_id),
    UNIQUE KEY uk_ycs_crypto_manifest_global_sequence (global_sequence),
    UNIQUE KEY uk_ycs_crypto_manifest_pair_digest (pair_digest),
    UNIQUE KEY uk_ycs_crypto_manifest_singleton_pair (singleton_id, pair_digest),
    CONSTRAINT chk_ycs_crypto_manifest_singleton CHECK (singleton_id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ycs_crypto_migration_runs (
    migration_run_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    admitted_singleton_id TINYINT UNSIGNED NOT NULL,
    admitted_pair_digest BINARY(32) NOT NULL,
    run_state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    manifest_digest BINARY(32) NOT NULL,
    lease_owner_digest BINARY(32) NULL,
    lease_expires_at DATETIME(6) NULL,
    scanned_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    migrated_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    verified_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    quarantined_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    optimistic_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (migration_run_id),
    CONSTRAINT fk_ycs_crypto_run_admitted_pair FOREIGN KEY (admitted_singleton_id, admitted_pair_digest)
        REFERENCES ycs_crypto_manifest_pair_admission (singleton_id, pair_digest),
    CONSTRAINT chk_ycs_crypto_run_state CHECK (run_state IN (
        'READY', 'RUNNING', 'PAUSED', 'ABORTED', 'COMPLETED', 'FAILED'
    )),
    CONSTRAINT chk_ycs_crypto_run_counts CHECK (
        migrated_count <= scanned_count
        AND verified_count <= migrated_count
        AND quarantined_count <= scanned_count
    ),
    CONSTRAINT chk_ycs_crypto_run_lease CHECK (
        (lease_owner_digest IS NULL AND lease_expires_at IS NULL)
        OR (lease_owner_digest IS NOT NULL AND lease_expires_at IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ycs_crypto_migration_checkpoints (
    migration_run_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    target_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    target_state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    last_legacy_row_id BIGINT UNSIGNED NULL,
    last_original_row_digest BINARY(32) NULL,
    lease_owner_digest BINARY(32) NULL,
    lease_expires_at DATETIME(6) NULL,
    scanned_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    migrated_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    verified_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    quarantined_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    optimistic_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (migration_run_id, target_type),
    CONSTRAINT fk_ycs_crypto_checkpoint_run FOREIGN KEY (migration_run_id)
        REFERENCES ycs_crypto_migration_runs (migration_run_id),
    CONSTRAINT fk_ycs_crypto_checkpoint_target FOREIGN KEY (target_type)
        REFERENCES ycs_crypto_migration_targets (target_type),
    CONSTRAINT chk_ycs_crypto_checkpoint_state CHECK (target_state IN (
        'DISCOVERED', 'BACKFILLED', 'VERIFIED', 'CUTOVER', 'SCRUBBED', 'COMPLETE'
    )),
    CONSTRAINT chk_ycs_crypto_checkpoint_cursor CHECK (
        (last_legacy_row_id IS NULL AND last_original_row_digest IS NULL)
        OR (last_legacy_row_id IS NOT NULL AND last_original_row_digest IS NOT NULL)
    ),
    CONSTRAINT chk_ycs_crypto_checkpoint_lease CHECK (
        (lease_owner_digest IS NULL AND lease_expires_at IS NULL)
        OR (lease_owner_digest IS NOT NULL AND lease_expires_at IS NOT NULL)
    ),
    CONSTRAINT chk_ycs_crypto_checkpoint_counts CHECK (
        migrated_count <= scanned_count
        AND verified_count <= migrated_count
        AND quarantined_count <= scanned_count
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ycs_crypto_migration_events (
    migration_event_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    migration_run_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    target_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    event_category VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    outcome VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    row_locator_digest BINARY(32) NULL,
    affected_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (migration_event_id),
    KEY idx_ycs_crypto_event_run (migration_run_id, created_at),
    CONSTRAINT fk_ycs_crypto_event_run FOREIGN KEY (migration_run_id)
        REFERENCES ycs_crypto_migration_runs (migration_run_id),
    CONSTRAINT fk_ycs_crypto_event_target FOREIGN KEY (target_type)
        REFERENCES ycs_crypto_migration_targets (target_type),
    CONSTRAINT chk_ycs_crypto_event_category CHECK (event_category IN (
        'RUN_STATE', 'TARGET_STATE', 'ROW_OUTCOME', 'LEASE', 'RECONCILIATION'
    )),
    CONSTRAINT chk_ycs_crypto_event_outcome CHECK (outcome IN (
        'ACCEPTED', 'SUCCEEDED', 'SKIPPED', 'REJECTED', 'QUARANTINED'
    ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ycs_crypto_registration_sessions (
    registration_session_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    tenant_draft_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    session_state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    upload_digest_purpose VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        DEFAULT 'REGISTRATION_UPLOAD_DIGEST',
    upload_digest_key_version BIGINT UNSIGNED NOT NULL,
    upload_credential_digest BINARY(32) NOT NULL,
    admitted_attempt_count TINYINT UNSIGNED NOT NULL DEFAULT 0,
    expires_at DATETIME(6) NOT NULL,
    optimistic_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (registration_session_id),
    UNIQUE KEY uk_ycs_crypto_session_tenant_binding (registration_session_id, tenant_draft_id),
    CONSTRAINT fk_ycs_crypto_session_upload_digest FOREIGN KEY
        (upload_digest_purpose, upload_digest_key_version)
        REFERENCES ycs_crypto_key_references (purpose, key_version),
    CONSTRAINT chk_ycs_crypto_session_state CHECK (session_state IN (
        'OPEN', 'CLAIMED', 'CLOSED', 'EXPIRED'
    )),
    CONSTRAINT chk_ycs_crypto_session_digest_purpose CHECK (
        upload_digest_purpose = 'REGISTRATION_UPLOAD_DIGEST'
    ),
    CONSTRAINT chk_ycs_crypto_session_attempts CHECK (admitted_attempt_count <= 15)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ycs_crypto_registration_upload_attempts (
    registration_session_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    object_purpose VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    admitted_attempt_count TINYINT UNSIGNED NOT NULL DEFAULT 0,
    optimistic_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (registration_session_id, object_purpose),
    CONSTRAINT fk_ycs_crypto_attempt_session FOREIGN KEY (registration_session_id)
        REFERENCES ycs_crypto_registration_sessions (registration_session_id),
    CONSTRAINT chk_ycs_crypto_attempt_purpose CHECK (object_purpose IN (
        'LEGAL_REPRESENTATIVE_ID_FRONT', 'LEGAL_REPRESENTATIVE_ID_BACK',
        'BUSINESS_LICENSE', 'SHORT_LINK_PROOF', 'TRADEMARK_PROOF'
    )),
    CONSTRAINT chk_ycs_crypto_attempt_ceiling CHECK (admitted_attempt_count <= 3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ycs_crypto_protected_objects (
    protected_object_id VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    registration_session_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    tenant_draft_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    object_purpose VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    object_state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    opaque_store_locator VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    envelope_digest BINARY(32) NOT NULL,
    envelope_size BIGINT UNSIGNED NOT NULL,
    media_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    replaces_object_id VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NULL,
    claim_reference VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NULL,
    current_marker TINYINT GENERATED ALWAYS AS (
        CASE WHEN object_state = 'STAGED' THEN 1 ELSE NULL END
    ) STORED,
    expires_at DATETIME(6) NOT NULL,
    optimistic_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (protected_object_id),
    UNIQUE KEY uk_ycs_crypto_object_binding
        (protected_object_id, registration_session_id, object_purpose),
    UNIQUE KEY uk_ycs_crypto_current_object
        (registration_session_id, object_purpose, current_marker),
    UNIQUE KEY uk_ycs_crypto_object_claim (claim_reference),
    CONSTRAINT fk_ycs_crypto_object_session FOREIGN KEY (registration_session_id, tenant_draft_id)
        REFERENCES ycs_crypto_registration_sessions (registration_session_id, tenant_draft_id),
    CONSTRAINT fk_ycs_crypto_object_replacement FOREIGN KEY
        (replaces_object_id, registration_session_id, object_purpose)
        REFERENCES ycs_crypto_protected_objects
            (protected_object_id, registration_session_id, object_purpose),
    CONSTRAINT chk_ycs_crypto_object_id CHECK (protected_object_id REGEXP '^pobj_v1_[A-Za-z0-9_-]+$'),
    CONSTRAINT chk_ycs_crypto_object_purpose CHECK (object_purpose IN (
        'LEGAL_REPRESENTATIVE_ID_FRONT', 'LEGAL_REPRESENTATIVE_ID_BACK',
        'BUSINESS_LICENSE', 'SHORT_LINK_PROOF', 'TRADEMARK_PROOF'
    )),
    CONSTRAINT chk_ycs_crypto_object_state CHECK (object_state IN (
        'STAGED', 'CLAIMED', 'REPLACED', 'EXPIRED', 'ORPHANED', 'DELETED'
    )),
    CONSTRAINT chk_ycs_crypto_object_claim_state CHECK (
        (object_state = 'CLAIMED' AND claim_reference IS NOT NULL)
        OR (object_state <> 'CLAIMED' AND claim_reference IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ycs_crypto_object_capabilities (
    capability_lookup_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    protected_object_id VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    tenant_binding_digest BINARY(32) NOT NULL,
    subject_binding_digest BINARY(32) NOT NULL,
    capability_purpose VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    digest_key_purpose VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        DEFAULT 'OBJECT_CAPABILITY_DIGEST',
    digest_key_version BIGINT UNSIGNED NOT NULL,
    capability_credential_digest BINARY(32) NOT NULL,
    capability_state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    optimistic_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (capability_lookup_id),
    KEY idx_ycs_crypto_capability_object (protected_object_id, capability_state, expires_at),
    CONSTRAINT fk_ycs_crypto_capability_object FOREIGN KEY (protected_object_id)
        REFERENCES ycs_crypto_protected_objects (protected_object_id),
    CONSTRAINT fk_ycs_crypto_capability_digest_key FOREIGN KEY
        (digest_key_purpose, digest_key_version)
        REFERENCES ycs_crypto_key_references (purpose, key_version),
    CONSTRAINT chk_ycs_crypto_capability_digest_purpose CHECK (
        digest_key_purpose = 'OBJECT_CAPABILITY_DIGEST'
    ),
    CONSTRAINT chk_ycs_crypto_capability_state CHECK (capability_state IN (
        'ACTIVE', 'REVOKED', 'EXPIRED'
    ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ycs_crypto_object_operations (
    operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    registration_session_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    object_purpose VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    protected_object_id VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NULL,
    operation_state VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    attempt_number TINYINT UNSIGNED NOT NULL,
    affected_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    optimistic_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (operation_id),
    UNIQUE KEY uk_ycs_crypto_operation_attempt
        (registration_session_id, object_purpose, attempt_number),
    CONSTRAINT fk_ycs_crypto_operation_attempt FOREIGN KEY
        (registration_session_id, object_purpose)
        REFERENCES ycs_crypto_registration_upload_attempts
            (registration_session_id, object_purpose),
    CONSTRAINT fk_ycs_crypto_operation_object FOREIGN KEY (protected_object_id)
        REFERENCES ycs_crypto_protected_objects (protected_object_id),
    CONSTRAINT chk_ycs_crypto_operation_state CHECK (operation_state IN (
        'RESERVED', 'OBJECT_STORED', 'METADATA_COMMITTED',
        'RECONCILE_DELETE', 'COMPLETED', 'FAILED'
    )),
    CONSTRAINT chk_ycs_crypto_operation_attempt_number CHECK (
        attempt_number BETWEEN 1 AND 3
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
