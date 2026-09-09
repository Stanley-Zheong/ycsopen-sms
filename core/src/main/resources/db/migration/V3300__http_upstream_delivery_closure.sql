ALTER TABLE message_send_outbox ADD COLUMN claim_token VARCHAR(64) NULL;
ALTER TABLE message_send_outbox ADD COLUMN claimed_at DATETIME NULL;
ALTER TABLE message_send_outbox ADD COLUMN attempt_count INT NOT NULL DEFAULT 0;
ALTER TABLE message_send_outbox ADD COLUMN provider_message_id VARCHAR(128) NULL;
ALTER TABLE message_send_outbox ADD COLUMN error_code VARCHAR(64) NULL;
ALTER TABLE message_send_outbox ADD COLUMN error_message VARCHAR(255) NULL;

CREATE UNIQUE INDEX uk_message_send_outbox_claim_token ON message_send_outbox(claim_token);
CREATE INDEX idx_message_send_outbox_provider_message ON message_send_outbox(provider_message_id);

ALTER TABLE delivery_reports ADD COLUMN receipt_digest CHAR(64) NULL;

CREATE UNIQUE INDEX uk_delivery_reports_receipt_digest ON delivery_reports(receipt_digest);
CREATE INDEX idx_delivery_reports_upstream_msg ON delivery_reports(upstream_msg_id);
CREATE INDEX idx_billing_records_task_status ON billing_records(task_ref_id, billing_status);
