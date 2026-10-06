package com.ycsopen.sms.core.service.message;

import com.ycsopen.sms.core.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageAcceptanceIdempotencyServiceTest {
    private EmbeddedDatabase database;
    private JdbcTemplate jdbc;
    private MessageAcceptanceIdempotencyService service;

    @AfterEach
    void tearDown() {
        if (database != null) {
            database.shutdown();
        }
    }

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("phase23-" + System.nanoTime())
                .build();
        jdbc = new JdbcTemplate(database);
        jdbc.execute("""
                CREATE TABLE message_submits(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  tenant_id BIGINT NOT NULL,
                  submit_id VARCHAR(64) NOT NULL,
                  request_digest CHAR(64),
                  source_protocol VARCHAR(16) NOT NULL,
                  product_type VARCHAR(32) NOT NULL,
                  template_id BIGINT,
                  signature_id BIGINT,
                  status VARCHAR(16) NOT NULL,
                  reject_reason VARCHAR(255),
                  UNIQUE(tenant_id, submit_id)
                )
                """);
        jdbc.execute("""
                CREATE TABLE message_submit_claim_leases(
                  submission_id BIGINT PRIMARY KEY,
                  processing_token VARCHAR(36) NOT NULL,
                  lease_expires_at TIMESTAMP NOT NULL,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE message_tasks(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  tenant_id BIGINT NOT NULL,
                  submit_id BIGINT,
                  message_id VARCHAR(64) NOT NULL,
                  send_status VARCHAR(16) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE message_send_outbox(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  tenant_id BIGINT NOT NULL,
                  task_id BIGINT NOT NULL UNIQUE,
                  message_id VARCHAR(64) NOT NULL UNIQUE,
                  channel_id BIGINT NOT NULL,
                  state VARCHAR(16) NOT NULL
                )
                """);
        service = new MessageAcceptanceIdempotencyService(jdbc,
                Clock.fixed(Instant.parse("2026-10-06T10:00:00Z"), ZoneOffset.UTC), Duration.ofMinutes(2));
    }

    @Test
    void duplicateSubmitIdWithSameDigestReturnsOriginalTaskWithoutCreatingAnotherRow() {
        var claim = service.claim(17L, "SUBMIT-1", "a".repeat(64));
        jdbc.update("""
                INSERT INTO message_tasks(tenant_id, submit_id, message_id, send_status)
                VALUES (?, ?, ?, ?)
                """, 17L, claim.submissionId(), "MSG_1700000000000_ABCDEF12", "PENDING");
        service.markAccepted(claim.submissionId());

        var duplicate = service.claim(17L, "SUBMIT-1", "a".repeat(64));

        assertThat(duplicate.existingResponse()).isPresent();
        assertThat(duplicate.existingResponse().orElseThrow().messageId())
                .isEqualTo("MSG_1700000000000_ABCDEF12");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM message_submits", Integer.class)).isEqualTo(1);
    }

    @Test
    void duplicateSubmitIdWithDifferentDigestFailsAsIdempotencyConflict() {
        service.claim(17L, "SUBMIT-2", "a".repeat(64));

        assertThatThrownBy(() -> service.claim(17L, "SUBMIT-2", "b".repeat(64)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo("IDEMPOTENCY_CONFLICT");
    }

    @Test
    void submitIdIsTrimmedBeforeLengthAndPatternValidation() {
        String tooLongAfterTrim = "A".repeat(65);

        assertThatThrownBy(() -> service.claim(17L, " " + tooLongAfterTrim + " ", "a".repeat(64)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo("INVALID_SUBMIT_ID");
    }

    @Test
    void acceptedTaskGetsOneDurableSendIntent() {
        service.enqueueSendIntent(17L, 91L, "MSG_1700000000000_ABCDEF12", 42L);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM message_send_outbox WHERE task_id=91", Integer.class))
                .isEqualTo(1);
        assertThatThrownBy(() -> service.enqueueSendIntent(17L, 91L, "MSG_1700000000001_ABCDEF12", 42L))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test
    void controlledRejectionIsDurableAndSameDigestReplayReturnsStoredCode() {
        var claim = service.claim(17L, "REJECTED-1", "c".repeat(64));

        service.recordRejected(claim, 8L, 9L, "ROUTING_REJECTED");

        assertThat(jdbc.queryForMap("SELECT status, reject_reason, template_id, signature_id FROM message_submits WHERE id=?",
                claim.submissionId())).containsEntry("status", "REJECTED")
                .containsEntry("reject_reason", "ROUTING_REJECTED")
                .containsEntry("template_id", 8L)
                .containsEntry("signature_id", 9L);
        assertThatThrownBy(() -> service.claim(17L, "REJECTED-1", "c".repeat(64)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo("ROUTING_REJECTED");
        assertThatThrownBy(() -> service.claim(17L, "REJECTED-1", "d".repeat(64)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo("IDEMPOTENCY_CONFLICT");
    }

    @Test
    void activeQueuedClaimCannotBeStolenButExpiredClaimGetsNewFenceToken() {
        var first = service.claim(17L, "LEASE-1", "e".repeat(64));

        assertThatThrownBy(() -> service.claim(17L, "LEASE-1", "e".repeat(64)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo("REQUEST_IN_PROGRESS");

        jdbc.update("UPDATE message_submit_claim_leases SET lease_expires_at=? WHERE submission_id=?",
                java.time.LocalDateTime.of(2026, 10, 6, 9, 59), first.submissionId());
        var recovered = service.claim(17L, "LEASE-1", "e".repeat(64));

        assertThat(recovered.submissionId()).isEqualTo(first.submissionId());
        assertThat(recovered.processingToken()).isNotEqualTo(first.processingToken());
        assertThatThrownBy(() -> service.lockClaim(first.submissionId(), first.processingToken()))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo("SUBMIT_CLAIM_LOST");
        service.lockClaim(recovered.submissionId(), recovered.processingToken());
    }
}
