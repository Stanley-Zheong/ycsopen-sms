ALTER TABLE message_submits
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6);

CREATE INDEX idx_message_submits_statistics_refresh
    ON message_submits(status, updated_at);

CREATE INDEX idx_message_submits_statistics_bucket
    ON message_submits(status, created_at);

CREATE INDEX idx_message_tasks_statistics_changed
    ON message_tasks(updated_at);

CREATE INDEX idx_message_tasks_statistics_bucket
    ON message_tasks(created_at);

CREATE INDEX idx_delivery_reports_statistics_changed
    ON delivery_reports(report_time, message_id);

CREATE INDEX idx_billing_records_statistics_changed
    ON billing_records(created_at, task_ref_id);

CREATE TABLE statistics_refresh_state (
    pipeline_code VARCHAR(64) PRIMARY KEY,
    scanned_through DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Issue 119 serialized statistics source-scan watermark';

INSERT INTO statistics_refresh_state(pipeline_code, scanned_through)
VALUES ('STATISTICS_AGGREGATION', '1970-01-01 00:00:00.000000');

CREATE TABLE statistics_refresh_checkpoints (
    business_date DATE PRIMARY KEY,
    source_window_start DATETIME(6) NOT NULL,
    source_window_end DATETIME(6) NOT NULL,
    source_changed_at DATETIME(6) NULL,
    refreshed_at DATETIME(6) NOT NULL,
    source_record_count INT NOT NULL DEFAULT 0,
    aggregate_row_count INT NOT NULL DEFAULT 0,
    refresh_status ENUM('SUCCESS') NOT NULL DEFAULT 'SUCCESS',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    KEY idx_statistics_refresh_freshness (refreshed_at),
    KEY idx_statistics_refresh_source_change (source_changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Issue 119 successful Asia/Shanghai business-date refresh checkpoints';

CREATE TABLE message_submit_claim_leases (
    submission_id BIGINT UNSIGNED PRIMARY KEY,
    processing_token CHAR(36) NOT NULL,
    lease_expires_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    KEY idx_message_submit_claim_lease_expiry (lease_expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Issue 119 fenced recovery lease for non-terminal HTTP submit claims';

INSERT INTO message_submit_claim_leases(submission_id, processing_token, lease_expires_at)
SELECT id, UUID(), '1970-01-01 00:00:00.000000'
  FROM message_submits
 WHERE status='QUEUED';
