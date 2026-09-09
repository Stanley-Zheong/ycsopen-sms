-- Phase 06 privileged audit storage; namespace SCHEMA-P06 / V1500-V1599.
CREATE TABLE privileged_operation_audits (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    actor_user_id BIGINT UNSIGNED NULL,
    actor_username VARCHAR(50) NOT NULL,
    actor_user_type VARCHAR(20) NULL,
    tenant_id BIGINT UNSIGNED NULL,
    operation VARCHAR(255) NOT NULL,
    resource_type VARCHAR(64) NULL,
    resource_id VARCHAR(128) NULL,
    request_method VARCHAR(8) NOT NULL,
    route_template VARCHAR(255) NOT NULL,
    sanitized_request JSON NOT NULL,
    result_code VARCHAR(32) NOT NULL,
    response_status INT NOT NULL,
    client_ip VARCHAR(45) NOT NULL,
    trace_id CHAR(32) NULL,
    latency_ms BIGINT UNSIGNED NOT NULL,
    occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_privileged_audit_actor_time (actor_user_id, occurred_at),
    KEY idx_privileged_audit_operation_time (operation, occurred_at),
    KEY idx_privileged_audit_result_time (result_code, occurred_at),
    KEY idx_privileged_audit_trace (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TRIGGER trg_privileged_audit_no_update
BEFORE UPDATE ON privileged_operation_audits
FOR EACH ROW
BEGIN
    IF NOT (
        OLD.result_code = 'STARTED'
        AND NEW.result_code IN ('SUCCESS', 'CLIENT_FAILURE', 'SERVER_FAILURE', 'DENIED')
        AND NEW.response_status BETWEEN 100 AND 599
        AND OLD.id <=> NEW.id
        AND OLD.actor_user_id <=> NEW.actor_user_id
        AND OLD.actor_username <=> NEW.actor_username
        AND OLD.actor_user_type <=> NEW.actor_user_type
        AND OLD.tenant_id <=> NEW.tenant_id
        AND OLD.operation <=> NEW.operation
        AND OLD.resource_type <=> NEW.resource_type
        AND OLD.resource_id <=> NEW.resource_id
        AND OLD.request_method <=> NEW.request_method
        AND OLD.route_template <=> NEW.route_template
        AND OLD.sanitized_request <=> NEW.sanitized_request
        AND OLD.client_ip <=> NEW.client_ip
        AND OLD.trace_id <=> NEW.trace_id
        AND OLD.occurred_at <=> NEW.occurred_at
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'privileged audit rows allow one terminal transition only';
    END IF;
END;

CREATE TRIGGER trg_privileged_audit_no_delete
BEFORE DELETE ON privileged_operation_audits
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'privileged audit rows are append-only';

CREATE PROCEDURE finalize_privileged_operation_audit(
    IN requested_id BIGINT UNSIGNED,
    IN requested_result VARCHAR(32),
    IN requested_status INT,
    IN requested_latency BIGINT UNSIGNED
)
SQL SECURITY DEFINER
MODIFIES SQL DATA
BEGIN
    IF requested_result NOT IN ('SUCCESS', 'CLIENT_FAILURE', 'SERVER_FAILURE', 'DENIED')
       OR requested_status NOT BETWEEN 100 AND 599 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'invalid privileged audit terminal state';
    END IF;
    UPDATE privileged_operation_audits
    SET result_code = requested_result,
        response_status = requested_status,
        latency_ms = requested_latency
    WHERE id = requested_id AND result_code = 'STARTED';
    IF ROW_COUNT() <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'privileged audit was not finalized exactly once';
    END IF;
END;

CREATE TABLE security_events (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    event_type VARCHAR(40) NOT NULL,
    actor_user_id BIGINT UNSIGNED NULL,
    tenant_id BIGINT UNSIGNED NULL,
    result_code VARCHAR(32) NOT NULL,
    client_ip VARCHAR(45) NULL,
    trace_id CHAR(32) NULL,
    safe_summary VARCHAR(255) NOT NULL,
    source_digest CHAR(64) NOT NULL,
    dedup_key VARCHAR(110) NOT NULL,
    occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_security_event_dedup (dedup_key),
    KEY idx_security_event_type_time (event_type, occurred_at),
    KEY idx_security_event_actor_time (actor_user_id, occurred_at),
    KEY idx_security_event_result_time (result_code, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
