-- Expand-only durable FIELD_ENCRYPTION_KEK reservations for split-write object publication.

ALTER TABLE ycs_crypto_object_operations
    ADD COLUMN field_key_purpose VARCHAR(48)
        CHARACTER SET ascii COLLATE ascii_bin NULL AFTER protected_object_id,
    ADD COLUMN field_key_version BIGINT UNSIGNED NULL AFTER field_key_purpose,
    ADD CONSTRAINT fk_ycs_crypto_operation_field_key
        FOREIGN KEY (field_key_purpose, field_key_version)
        REFERENCES ycs_crypto_key_references (purpose, key_version),
    ADD CONSTRAINT chk_ycs_crypto_operation_field_key CHECK (
        (field_key_purpose IS NULL AND field_key_version IS NULL)
        OR (field_key_purpose = 'FIELD_ENCRYPTION_KEK' AND field_key_version IS NOT NULL)
    );
