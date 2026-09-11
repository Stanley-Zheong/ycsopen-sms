ALTER TABLE uplink_records
  ADD COLUMN source_protocol VARCHAR(16) NULL AFTER tenant_id,
  ADD COLUMN source_connector VARCHAR(64) NULL AFTER source_protocol,
  ADD COLUMN source_event_id VARCHAR(128) NULL AFTER source_connector,
  ADD COLUMN message_id VARCHAR(128) NULL AFTER source_event_id,
  ADD COLUMN phone_masked VARCHAR(32) NULL AFTER message_id,
  ADD COLUMN phone_hash VARCHAR(128) NULL AFTER phone_masked,
  ADD COLUMN content_keyword VARCHAR(64) NULL AFTER content,
  ADD COLUMN state VARCHAR(32) NOT NULL DEFAULT 'NORMALIZED' AFTER content_keyword,
  ADD COLUMN carrier VARCHAR(32) NULL AFTER state,
  ADD COLUMN province VARCHAR(64) NULL AFTER carrier,
  ADD COLUMN city VARCHAR(64) NULL AFTER province,
  ADD COLUMN destination VARCHAR(255) NULL AFTER city,
  ADD COLUMN signature_id BIGINT UNSIGNED NULL AFTER channel_id,
  ADD COLUMN product_code VARCHAR(64) NULL AFTER signature_id,
  ADD COLUMN push_state VARCHAR(32) NOT NULL DEFAULT 'NOT_REQUESTED' AFTER product_code,
  ADD COLUMN push_event_id BIGINT UNSIGNED NULL AFTER push_state,
  ADD COLUMN receive_time DATETIME NULL AFTER push_event_id,
  ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER receive_time,
  ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER created_at;

UPDATE uplink_records
   SET source_protocol = COALESCE(source_protocol, 'LEGACY'),
       source_connector = COALESCE(source_connector, 'V1_UPLINK'),
       source_event_id = COALESCE(source_event_id, CONCAT('LEGACY-', id)),
       phone_masked = COALESCE(phone_masked, 'legacy-protected'),
       phone_hash = COALESCE(phone_hash, SHA2(CONCAT('legacy-uplink:', id), 256)),
       content_keyword = COALESCE(content_keyword, LEFT(content, 32)),
       city = COALESCE(city, location),
       destination = COALESCE(destination, target_number),
       push_state = CASE push_status
         WHEN 'SUCCESS' THEN 'SUCCESS'
         WHEN 'FAILED' THEN 'PUSH_FAILED'
         ELSE 'NOT_REQUESTED'
       END,
       receive_time = COALESCE(receive_time, received_at, CURRENT_TIMESTAMP),
       created_at = COALESCE(created_at, received_at, CURRENT_TIMESTAMP),
       updated_at = COALESCE(updated_at, push_time, received_at, CURRENT_TIMESTAMP)
 WHERE source_protocol IS NULL
    OR source_connector IS NULL
    OR source_event_id IS NULL
    OR phone_masked IS NULL
    OR phone_hash IS NULL
    OR receive_time IS NULL;

ALTER TABLE uplink_records
  MODIFY COLUMN source_protocol VARCHAR(16) NOT NULL,
  MODIFY COLUMN source_connector VARCHAR(64) NOT NULL,
  MODIFY COLUMN source_event_id VARCHAR(128) NOT NULL,
  MODIFY COLUMN phone_masked VARCHAR(32) NOT NULL,
  MODIFY COLUMN phone_hash VARCHAR(128) NOT NULL,
  MODIFY COLUMN receive_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE UNIQUE INDEX uk_uplink_record_source ON uplink_records(tenant_id, source_protocol, source_connector, source_event_id);
CREATE INDEX idx_uplink_record_search ON uplink_records(tenant_id, receive_time, push_state);
CREATE INDEX idx_uplink_record_phone ON uplink_records(tenant_id, phone_hash);
CREATE UNIQUE INDEX uk_uplink_record_push_event ON uplink_records(push_event_id);

CREATE TABLE tenant_uplink_auto_reply_configs (
  tenant_id BIGINT PRIMARY KEY,
  enabled BOOLEAN NOT NULL DEFAULT FALSE,
  keyword VARCHAR(64),
  template_id VARCHAR(64),
  response_content VARCHAR(255),
  loop_guard_minutes INT NOT NULL DEFAULT 30,
  audit_reason VARCHAR(255),
  updated_by VARCHAR(64) NOT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE tenant_uplink_auto_reply_attempts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  phone_hash VARCHAR(128) NOT NULL,
  uplink_record_id BIGINT,
  keyword VARCHAR(64) NOT NULL,
  response_content VARCHAR(255),
  template_id VARCHAR(64),
  decision VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_uplink_auto_reply_guard (tenant_id, phone_hash, created_at)
);
