package com.ycsopen.sms.core.service.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecureAsyncExportServiceTest {
    private JdbcTemplate jdbc;
    private SecureAsyncExportService service;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:phase46-" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
                "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        createSchema();
        service = new SecureAsyncExportService(jdbc, new ObjectMapper().findAndRegisterModules(), null);
    }

    @Test
    void createsImmutableEncryptedArtifactsForEverySupportedFormatAndMasksProtectedFields() {
        for (String format : List.of("EXCEL", "CSV", "JSON", "PDF")) {
            var job = service.create(command("REQ-" + format, "SEND_DETAIL", format, rows(false)));
            var downloaded = service.download(job.id(), new SecureAsyncExportService.DownloadCommand("7", 42L));
            String clear = service.decryptForVerification(job.id());

            assertThat(job.status()).isEqualTo("COMPLETED");
            assertThat(job.recordCount()).isEqualTo(2);
            assertThat(job.authorizationSnapshot()).contains("\"tenantId\":42");
            assertThat(job.sourceSnapshot()).contains("sourceDigest");
            assertThat(job.artifactManifest()).contains("\"rowCount\":2");
            assertThat(downloaded.encryptionState()).isEqualTo("ENCRYPTED");
            assertThat(downloaded.artifactBase64()).doesNotContain("13800138000");
            assertThat(clear).contains("138****8000").contains("MSG-1");
            assertThat(clear).doesNotContain("13800138000");
        }
    }

    @Test
    void requestIdIsIdempotentOnlyForTheSameSnapshotKey() {
        var first = service.create(command("REQ-IDEMPOTENT", "RECEIPT_DETAIL", "CSV", rows(false)));
        var second = service.create(command("REQ-IDEMPOTENT", "RECEIPT_DETAIL", "CSV", rows(false)));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM export_tasks WHERE request_id='REQ-IDEMPOTENT'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void failedJobsExposeReasonAndRetryKeepsSnapshotAuthorizationAndArtifactBoundary() {
        var failed = service.create(command("REQ-FAILED", "BALANCE_AUDIT", "CSV", rows(true)));

        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.failureReason()).contains("测试注入");
        assertThatThrownBy(() -> service.download(failed.id(), new SecureAsyncExportService.DownloadCommand("7", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("导出任务未完成");

        var retried = service.retry(failed.id(), new SecureAsyncExportService.RetryCommand("7", "重新生成加密包"));

        assertThat(retried.status()).isEqualTo("COMPLETED");
        assertThat(retried.retryCount()).isEqualTo(1);
        assertThat(retried.partialFailureCount()).isZero();
        assertThat(retried.authorizationSnapshot()).contains("\"permission\":\"secure-async-export:create\"");
    }

    @Test
    void largeExportsStayRunningWithSplitMetadataUntilWorkerCompletion() {
        List<Map<String, Object>> manyRows = new java.util.ArrayList<>();
        for (int index = 0; index < 1_050; index++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", index);
            row.put("mobile", "13800138000");
            row.put("state", "DELIVERED");
            manyRows.add(row);
        }

        var job = service.create(command("REQ-LARGE", "SEND_DETAIL", "CSV", manyRows));

        assertThat(job.status()).isEqualTo("RUNNING");
        assertThat(job.progress()).isEqualTo(10);
        assertThat(job.splitCount()).isGreaterThan(1);
        assertThat(job.sourceSnapshot()).contains("\"sourceRowCount\":1050");
    }

    @Test
    void downloadRechecksTenantScopeAndExpiry() {
        var job = service.create(command("REQ-DOWNLOAD", "UNSUBSCRIBE_EVIDENCE", "CSV", rows(false)));

        assertThatThrownBy(() -> service.download(job.id(), new SecureAsyncExportService.DownloadCommand("7", 99L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能下载其他机构");

        jdbc.update("UPDATE export_tasks SET expires_at=? WHERE id=?",
                java.sql.Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)), job.id());
        assertThatThrownBy(() -> service.download(job.id(), new SecureAsyncExportService.DownloadCommand("7", 42L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已过期");
    }

    private SecureAsyncExportService.ExportCreateCommand command(String requestId, String type, String format,
                                                                 List<Map<String, Object>> rows) {
        boolean forceFailure = rows.stream().anyMatch(row -> Boolean.TRUE.equals(row.get("forceFailure")));
        return new SecureAsyncExportService.ExportCreateCommand(requestId, 42L, type, "TEST_PRODUCER",
                "测试导出", "7", format, Map.of("forceFailure", forceFailure), List.of("id ASC"),
                "secure-async-export:create", List.of("mobile"), rows);
    }

    private List<Map<String, Object>> rows(boolean forceFailure) {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("id", 1);
        first.put("message_id", "MSG-1");
        first.put("mobile", "13800138000");
        first.put("state", "DELIVERED");
        if (forceFailure) first.put("forceFailure", true);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("id", 2);
        second.put("message_id", "MSG-2");
        second.put("mobile", "13900139000");
        second.put("state", "FAILED");
        return List.of(first, second);
    }

    private void createSchema() {
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
