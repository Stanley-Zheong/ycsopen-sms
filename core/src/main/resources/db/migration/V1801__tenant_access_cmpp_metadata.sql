-- Phase 09 additive CMPP metadata; encrypted account/password columns are retained.
ALTER TABLE tenant_protocol_credentials ADD COLUMN spid VARCHAR(32) NULL AFTER protocol;
ALTER TABLE tenant_protocol_credentials ADD COLUMN endpoint_host VARCHAR(255) NULL AFTER spid;
ALTER TABLE tenant_protocol_credentials ADD COLUMN endpoint_port INT NULL AFTER endpoint_host;
ALTER TABLE tenant_protocol_credentials ADD COLUMN revoked_at DATETIME NULL AFTER created_at;
