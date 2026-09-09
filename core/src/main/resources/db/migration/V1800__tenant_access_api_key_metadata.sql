-- Phase 09 additive HTTP credential metadata; existing encrypted column is retained.
ALTER TABLE tenant_api_keys ADD COLUMN description VARCHAR(255) NULL AFTER key_name;
ALTER TABLE tenant_api_keys ADD COLUMN revoked_at DATETIME NULL AFTER last_used_time;
