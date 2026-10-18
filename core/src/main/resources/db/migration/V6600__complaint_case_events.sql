CREATE TABLE complaint_case_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    complaint_id BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    actor VARCHAR(64) NULL,
    occurred_at DATETIME NOT NULL,
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NULL,
    evidence_text VARCHAR(2000) NULL,
    target_ref VARCHAR(64) NULL,
    result VARCHAR(32) NOT NULL,
    review_id VARCHAR(128) NULL,
    failure_reason VARCHAR(500) NULL,
    related_disposal_id BIGINT NULL,
    source_record_type VARCHAR(32) NOT NULL,
    source_record_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_complaint_case_event_source
    ON complaint_case_events(event_type, source_record_type, source_record_id);
CREATE INDEX idx_complaint_case_event_timeline
    ON complaint_case_events(complaint_id, occurred_at, id);

INSERT INTO complaint_case_events(
    complaint_id, event_type, actor, occurred_at, from_status, to_status,
    evidence_text, result, source_record_type, source_record_id)
SELECT id, 'REGISTERED', created_by, created_at, NULL, 'PENDING',
       CONCAT('来源：', source, '；摘要：', COALESCE(summary, '')),
       'SUCCESS', 'COMPLAINT', id
  FROM complaints;

INSERT INTO complaint_case_events(
    complaint_id, event_type, actor, occurred_at, from_status, to_status,
    evidence_text, result, source_record_type, source_record_id)
SELECT id, 'ACCEPTED', accepted_by, accepted_at, 'PENDING', 'PROCESSING',
       CASE WHEN handled_at IS NULL THEN opinion ELSE NULL END,
       'SUCCESS', 'COMPLAINT', id
  FROM complaints
 WHERE accepted_at IS NOT NULL;

INSERT INTO complaint_case_events(
    complaint_id, event_type, actor, occurred_at, from_status, to_status,
    evidence_text, result, source_record_type, source_record_id)
SELECT id, 'HANDLED', handled_by, handled_at, 'PROCESSING', 'PROCESSED',
       CONCAT('处理意见：', COALESCE(opinion, ''),
              '；处置动作：', COALESCE(remediation, ''),
              '；整改要求：', COALESCE(requirement, '')),
       'SUCCESS', 'COMPLAINT', id
  FROM complaints
 WHERE handled_at IS NOT NULL;

INSERT INTO complaint_case_events(
    complaint_id, event_type, actor, occurred_at, from_status, to_status,
    target_ref, result, review_id, failure_reason, related_disposal_id,
    source_record_type, source_record_id)
SELECT complaint_id,
       CASE WHEN status = 'APPLIED' THEN 'REMEDIATION_APPLIED' ELSE 'REMEDIATION_FAILED' END,
       disposed_by, disposed_at, 'PROCESSED', 'PROCESSED', target_ref,
       CASE WHEN status = 'APPLIED' THEN 'APPLIED' ELSE 'FAILED' END,
       CASE WHEN status IN ('APPLIED', 'FAILED') THEN authorized_review_id ELSE NULL END,
       CASE WHEN failure_reason IS NULL THEN NULL
            ELSE '处置执行失败，请根据安全审计日志排查' END,
       id, 'DISPOSAL_RECORD', id
  FROM disposal_records
 WHERE status IN ('APPLIED', 'FAILED') OR recovered_at IS NOT NULL;

INSERT INTO complaint_case_events(
    complaint_id, event_type, actor, occurred_at, from_status, to_status,
    evidence_text, target_ref, result, review_id, related_disposal_id,
    source_record_type, source_record_id)
SELECT complaint_id, 'RECOVERED', recovered_by, recovered_at, NULL, NULL,
       resume_condition, target_ref, 'RECOVERED', authorized_review_id, id,
       'DISPOSAL_RECORD', id
  FROM disposal_records
 WHERE recovered_at IS NOT NULL;

INSERT INTO complaint_case_events(
    complaint_id, event_type, actor, occurred_at, from_status, to_status,
    evidence_text, result, source_record_type, source_record_id)
SELECT id, 'CLOSED', closed_by, closed_at, 'PROCESSED', 'CLOSED',
       closed_note, 'SUCCESS', 'COMPLAINT', id
  FROM complaints
 WHERE closed_at IS NOT NULL;
