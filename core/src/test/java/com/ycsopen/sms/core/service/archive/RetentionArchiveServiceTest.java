package com.ycsopen.sms.core.service.archive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.service.export.SecureAsyncExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RetentionArchiveServiceTest {
    private JdbcTemplate jdbc;
    private RetentionArchiveService service;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:phase47-" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
                "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        createSchema();
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        SecureAsyncExportService exports = new SecureAsyncExportService(jdbc, mapper, null);
        service = new RetentionArchiveService(jdbc, mapper, exports);
    }

    @Test
    void archivesEncryptedChecksummedRecordsThenRestoresAndExportsThem() {
        var policy = service.savePolicy(new RetentionArchiveService.PolicyCommand(
                "MESSAGE_TASKS", 730, 3, null, "7"));
        var manifest = service.archive(new RetentionArchiveService.ArchiveCommand(
                "MESSAGE_TASKS", 42L, "2026-01", rows(), false, "7"));

        assertThat(policy.retentionDays()).isEqualTo(730);
        assertThat(manifest.archiveStatus()).isEqualTo("COMPLETED");
        assertThat(manifest.manifestJson()).contains("\"rowCount\":2").contains("\"hotMonths\":3");
        assertThat(manifest.sourceIdentityJson()).contains("MSG-1").contains("message_tasks");
        assertThat(manifest.archiveCiphertextBase64()).doesNotContain("MSG-1");

        var verified = service.verify(manifest.id());
        var restored = service.restore(manifest.id(), new RetentionArchiveService.RestoreCommand("7"));
        var exported = service.export(manifest.id(), new RetentionArchiveService.RestoreCommand("7"));

        assertThat(verified.verifiedAt()).isNotNull();
        assertThat(restored.status()).isEqualTo("COMPLETED");
        assertThat(restored.restoredRecordCount()).isEqualTo(2);
        assertThat(exported.exportTaskId()).isNotNull();
        assertThat(service.decryptRowsForVerification(manifest.id())).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM export_tasks WHERE export_type='ARCHIVE_RESTORE'",
                Integer.class)).isOne();
    }

    @Test
    void legalHoldBlocksDeletionAndCorruptionIsDetected() {
        service.savePolicy(new RetentionArchiveService.PolicyCommand(
                "DELIVERY_REPORTS", 730, 2, LocalDateTime.now().plusDays(1), "7"));
        var manifest = service.archive(new RetentionArchiveService.ArchiveCommand(
                "DELIVERY_REPORTS", 42L, "2025-12", rows(), false, "7"));

        jdbc.update("UPDATE archive_manifests SET retention_until=? WHERE id=?",
                java.sql.Timestamp.valueOf(LocalDateTime.now().minusDays(1)), manifest.id());
        var verified = service.verify(manifest.id());
        assertThat(verified.deletionEligible()).isFalse();

        jdbc.update("UPDATE archive_manifests SET archive_ciphertext=? WHERE id=?",
                new byte[] {1, 2, 3, 4}, manifest.id());
        var corrupted = service.verify(manifest.id());
        assertThat(corrupted.archiveStatus()).isEqualTo("CORRUPTED");
        assertThat(corrupted.failureReason()).contains("密文无法解密");
    }

    @Test
    void failedArchiveKeepsReasonAndRestoreDoesNotPretendSuccess() {
        var failed = service.archive(new RetentionArchiveService.ArchiveCommand(
                "UNSUBSCRIBE_RECORDS", 42L, "2026-01", rows(), true, "7"));

        var restore = service.restore(failed.id(), new RetentionArchiveService.RestoreCommand("7"));

        assertThat(failed.archiveStatus()).isEqualTo("FAILED");
        assertThat(failed.failureReason()).contains("测试注入");
        assertThat(restore.status()).isEqualTo("FAILED");
        assertThat(restore.resultMessage()).contains("不可恢复");
    }

    @Test
    void scanArchivesEligibleHotRowsFromWhitelistedSourceTables() {
        jdbc.execute("""
                CREATE TABLE message_tasks(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  message_id VARCHAR(64),
                  tenant_id BIGINT,
                  send_status VARCHAR(24),
                  cost DECIMAL(10,4),
                  created_at TIMESTAMP
                )
                """);
        jdbc.update("""
                INSERT INTO message_tasks(message_id, tenant_id, send_status, cost, created_at)
                VALUES ('MSG-OLD', 42, 'DELIVERED', 1.2500, ?), ('MSG-NEW', 42, 'SENT', 0.5000, ?)
                """, java.sql.Timestamp.valueOf(LocalDateTime.now().minusMonths(4)),
                java.sql.Timestamp.valueOf(LocalDateTime.now()));

        service.savePolicy(new RetentionArchiveService.PolicyCommand("MESSAGE_TASKS", 730, 3, null, "7"));
        var manifest = service.archiveEligible(new RetentionArchiveService.ScanCommand("MESSAGE_TASKS", 42L, "7"));

        assertThat(manifest.rowCount()).isOne();
        assertThat(manifest.sourceIdentityJson()).contains("MSG-OLD").doesNotContain("MSG-NEW");
        assertThat(service.decryptRowsForVerification(manifest.id()).getFirst()).containsEntry("message_id", "MSG-OLD");
    }

    private List<Map<String, Object>> rows() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("id", 1);
        first.put("message_id", "MSG-1");
        first.put("mobile", "138****8000");
        first.put("status", "DELIVERED");
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("id", 2);
        second.put("message_id", "MSG-2");
        second.put("mobile", "139****9000");
        second.put("status", "FAILED");
        return List.of(first, second);
    }

    private void createSchema() {
        jdbc.execute("""
                CREATE TABLE archive_policies(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  data_domain VARCHAR(64) UNIQUE NOT NULL,
                  source_table VARCHAR(64) NOT NULL,
                  retention_days INT NOT NULL DEFAULT 730,
                  hot_months INT NOT NULL DEFAULT 3,
                  partition_unit VARCHAR(16) NOT NULL DEFAULT 'MONTH',
                  legal_hold_until TIMESTAMP,
                  encryption_required BOOLEAN NOT NULL DEFAULT TRUE,
                  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
                  updated_by VARCHAR(64),
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE archive_manifests(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  policy_id BIGINT NOT NULL,
                  data_domain VARCHAR(64) NOT NULL,
                  source_table VARCHAR(64) NOT NULL,
                  partition_key VARCHAR(16) NOT NULL,
                  tenant_id BIGINT,
                  archive_status VARCHAR(16) NOT NULL,
                  row_count BIGINT NOT NULL DEFAULT 0,
                  source_identity_json VARCHAR(4000) NOT NULL,
                  manifest_json VARCHAR(4000) NOT NULL,
                  archive_ciphertext BLOB NOT NULL,
                  checksum_sha256 CHAR(64) NOT NULL,
                  encryption_key_version VARCHAR(32) NOT NULL DEFAULT 'archive-v1',
                  retention_until TIMESTAMP NOT NULL,
                  legal_hold_until TIMESTAMP,
                  deletion_eligible BOOLEAN NOT NULL DEFAULT FALSE,
                  failure_reason VARCHAR(255),
                  created_by VARCHAR(64),
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  verified_at TIMESTAMP,
                  restored_at TIMESTAMP,
                  exported_task_id BIGINT
                )
                """);
        jdbc.execute("""
                CREATE TABLE archive_restore_jobs(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  manifest_id BIGINT NOT NULL,
                  request_type VARCHAR(16) NOT NULL,
                  status VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
                  requested_by VARCHAR(64),
                  result_message VARCHAR(255),
                  restored_record_count BIGINT NOT NULL DEFAULT 0,
                  export_task_id BIGINT,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  completed_at TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE export_tasks(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  request_id VARCHAR(64) UNIQUE,
                  tenant_id BIGINT,
                  export_type VARCHAR(64) NOT NULL,
                  producer VARCHAR(64) NOT NULL DEFAULT 'LEGACY',
                  job_name VARCHAR(128) NOT NULL DEFAULT '导出任务',
                  created_by VARCHAR(64),
                  file_format VARCHAR(16) NOT NULL,
                  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
                  progress_pct INT NOT NULL DEFAULT 0,
                  record_count BIGINT NOT NULL DEFAULT 0,
                  file_size_bytes BIGINT,
                  file_url VARCHAR(255),
                  file_sha256 CHAR(64),
                  encryption_state VARCHAR(32) NOT NULL DEFAULT 'PASSWORD_PROTECTED',
                  retry_count INT NOT NULL DEFAULT 0,
                  split_count INT NOT NULL DEFAULT 1,
                  partial_failure_count INT NOT NULL DEFAULT 0,
                  failure_reason VARCHAR(255),
                  authorization_snapshot VARCHAR(4000),
                  source_snapshot VARCHAR(4000),
                  artifact_manifest VARCHAR(4000),
                  artifact_ciphertext BLOB,
                  download_token_hash CHAR(64),
                  expires_at TIMESTAMP,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  completed_at TIMESTAMP,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }
}
