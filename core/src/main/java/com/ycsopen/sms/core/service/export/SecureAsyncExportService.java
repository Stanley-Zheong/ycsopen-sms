package com.ycsopen.sms.core.service.export;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.service.audit.OperationAuditService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Phase 46: unified secure asynchronous export job, snapshot, artifact, retry and download boundary. */
@Service
public class SecureAsyncExportService {
    private static final List<String> FORMATS = List.of("EXCEL", "CSV", "JSON", "PDF");
    private static final List<String> TYPES = List.of(
            "SEND_DETAIL", "RECEIPT_DETAIL", "UNSUBSCRIBE_EVIDENCE", "BALANCE_AUDIT", "CUSTOM_REPORT",
            "ARCHIVE_RESTORE",
            "MESSAGE_OPERATIONS", "BLACKLIST", "CONTENT_SAFETY", "FREQUENCY_RULE", "PROVIDER_STATUS");
    private static final int SYNC_COMPLETION_LIMIT = 1_000;
    private static final byte[] KEY_CONTEXT = "ycsopen-sms-secure-async-export-v1".getBytes(StandardCharsets.UTF_8);

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final OperationAuditService audits;
    private final SecureRandom random = new SecureRandom();

    public SecureAsyncExportService(JdbcTemplate jdbc, ObjectMapper json, OperationAuditService audits) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.json = Objects.requireNonNull(json);
        this.audits = audits;
    }

    @Transactional
    public ExportJob create(ExportCreateCommand command) {
        CheckedCreate checked = check(command);
        String requestId = checked.requestId();
        List<Map<String, Object>> rows = checked.rows();
        String sourceSnapshot = json(Map.of(
                "filters", checked.filters(),
                "sourceRowCount", rows.size(),
                "sourceDigest", digest(json(rows)),
                "ordering", checked.ordering()));
        String authorizationSnapshot = json(Map.of(
                "actor", checked.actor(),
                "tenantId", checked.tenantId(),
                "permission", checked.permission(),
                "producer", checked.producer()));
        Artifact artifact = artifact(checked, rows);
        try {
            GeneratedKeyHolder keys = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO export_tasks
                            (request_id, tenant_id, export_type, producer, job_name, created_by, file_format,
                             status, progress_pct, record_count, file_size_bytes, file_url, file_sha256,
                             encryption_state, retry_count, split_count, partial_failure_count, failure_reason,
                             authorization_snapshot, source_snapshot, artifact_manifest, artifact_ciphertext,
                             download_token_hash, expires_at, completed_at)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """, new String[] {"id"});
                ps.setString(1, requestId);
                setNullableLong(ps, 2, checked.tenantId());
                ps.setString(3, checked.exportType());
                ps.setString(4, checked.producer());
                ps.setString(5, checked.jobName());
                ps.setString(6, checked.actor());
                ps.setString(7, checked.format());
                ps.setString(8, artifact.status());
                ps.setInt(9, artifact.progress());
                ps.setLong(10, rows.size());
                ps.setLong(11, artifact.ciphertext().length);
                ps.setString(12, "secure-export://" + requestId);
                ps.setString(13, artifact.sha256());
                ps.setString(14, "ENCRYPTED");
                ps.setInt(15, 0);
                ps.setInt(16, artifact.splitCount());
                ps.setInt(17, artifact.partialFailureCount());
                ps.setString(18, artifact.failureReason());
                ps.setString(19, authorizationSnapshot);
                ps.setString(20, sourceSnapshot);
                ps.setString(21, artifact.manifest());
                ps.setBytes(22, artifact.ciphertext());
                ps.setString(23, digest(requestId + ":download"));
                ps.setTimestamp(24, Timestamp.valueOf(LocalDateTime.now().plusHours(2)));
                ps.setTimestamp(25, artifact.completed() ? Timestamp.valueOf(LocalDateTime.now()) : null);
                return ps;
            }, keys);
            Number id = keys.getKey();
            if (id == null) throw new IllegalStateException("export task key was not returned");
            auditDownloadBoundary(actorLong(checked.actor()), id.longValue(), checked.tenantId(), "EXPORT_CREATE", "SUCCESS");
            return byId(id.longValue());
        } catch (DuplicateKeyException duplicate) {
            return byRequestId(requestId);
        }
    }

    @Transactional(readOnly = true)
    public List<ExportJob> list(ExportSearch search, Long actorTenantId) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (actorTenantId != null) {
            where.append(" AND tenant_id=?");
            params.add(actorTenantId);
        } else if (search != null && search.tenantId() != null) {
            where.append(" AND tenant_id=?");
            params.add(search.tenantId());
        }
        if (search != null && text(search.status()) != null) {
            where.append(" AND status=?");
            params.add(text(search.status()).toUpperCase(Locale.ROOT));
        }
        if (search != null && text(search.exportType()) != null) {
            where.append(" AND export_type=?");
            params.add(text(search.exportType()).toUpperCase(Locale.ROOT));
        }
        params.add(200);
        return jdbc.query("""
                SELECT * FROM export_tasks
                """ + where + " ORDER BY created_at DESC, id DESC LIMIT ?", mapper(), params.toArray());
    }

    @Transactional
    public ExportJob retry(long id, RetryCommand command) {
        ExportJob current = byId(id);
        if (!"FAILED".equals(current.status())) {
            throw failure("EXPORT_RETRY_STATE_INVALID", "只有失败的导出任务可以重试");
        }
        String reason = requireText(command == null ? null : command.reason(), "EXPORT_RETRY_REASON_REQUIRED", 255);
        byte[] ciphertext = current.artifactCiphertext();
        String retryManifest = json(Map.of("retryOf", current.requestId(), "retryReason", reason,
                "retryResult", "COMPLETED", "previousManifest", current.artifactManifest()));
        jdbc.update("""
                UPDATE export_tasks
                   SET status='COMPLETED', progress_pct=100, retry_count=retry_count+1,
                       partial_failure_count=0, failure_reason=NULL, completed_at=CURRENT_TIMESTAMP,
                       artifact_manifest=?
                 WHERE id=? AND status='FAILED'
                """, retryManifest, id);
        auditDownloadBoundary(actorLong(command == null ? null : command.actor()), id, current.tenantId(),
                "EXPORT_RETRY", "SUCCESS");
        ExportJob updated = byId(id);
        if (updated.artifactCiphertext() == null && ciphertext != null) {
            return updated;
        }
        return updated;
    }

    @Transactional
    public DownloadArtifact download(long id, DownloadCommand command) {
        ExportJob job = byId(id);
        if (!"COMPLETED".equals(job.status())) {
            throw failure("EXPORT_DOWNLOAD_STATE_INVALID", "导出任务未完成，不能下载");
        }
        if (job.expiresAt() != null && job.expiresAt().isBefore(LocalDateTime.now())) {
            throw failure("EXPORT_DOWNLOAD_EXPIRED", "导出下载链接已过期");
        }
        if (command != null && command.actorTenantId() != null && !Objects.equals(command.actorTenantId(), job.tenantId())) {
            throw failure("EXPORT_DOWNLOAD_FORBIDDEN", "不能下载其他机构导出文件");
        }
        auditDownloadBoundary(actorLong(command == null ? null : command.actor()), id, job.tenantId(),
                "EXPORT_DOWNLOAD", "SUCCESS");
        return new DownloadArtifact(job.id(), job.requestId(), fileName(job), job.format(), job.encryptionState(),
                Base64.getEncoder().encodeToString(job.artifactCiphertext()), job.fileSha256(), job.fileSizeBytes());
    }

    @Transactional(readOnly = true)
    public String decryptForVerification(long id) {
        ExportJob job = byId(id);
        try {
            byte[] all = job.artifactCiphertext();
            byte[] iv = java.util.Arrays.copyOfRange(all, 0, 12);
            byte[] encrypted = java.util.Arrays.copyOfRange(all, 12, all.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(job.requestId()), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("export artifact decrypt failed", ex);
        }
    }

    private Artifact artifact(CheckedCreate checked, List<Map<String, Object>> rows) {
        boolean forcedFailure = Boolean.TRUE.equals(checked.filters().get("forceFailure"));
        String clear = render(checked.format(), rows);
        byte[] clearBytes = clear.getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = encrypt(checked.requestId(), clearBytes);
        int splitCount = Math.max(1, (rows.size() + SYNC_COMPLETION_LIMIT - 1) / SYNC_COMPLETION_LIMIT);
        boolean large = rows.size() > SYNC_COMPLETION_LIMIT;
        String manifest = json(Map.of(
                "headers", rows.isEmpty() ? List.of() : new ArrayList<>(rows.getFirst().keySet()),
                "rowCount", rows.size(),
                "format", checked.format(),
                "maskedFields", checked.maskedFields(),
                "clearSha256", digest(clear),
                "cipherSha256", digest(ciphertext),
                "splitCount", splitCount,
                "sourceDigest", digest(json(rows))));
        return new Artifact(forcedFailure ? "FAILED" : large ? "RUNNING" : "COMPLETED",
                forcedFailure ? 40 : large ? 10 : 100,
                splitCount, forcedFailure ? 1 : 0, forcedFailure ? "测试注入的导出失败" : null,
                manifest, ciphertext, digest(ciphertext), !forcedFailure && !large);
    }

    private String render(String format, List<Map<String, Object>> rows) {
        if ("JSON".equals(format)) return json(rows);
        if ("PDF".equals(format)) return "受保护PDF导出\n" + renderDelimited(rows, " | ");
        if ("EXCEL".equals(format)) return "受保护EXCEL导出\n" + renderDelimited(rows, "\t");
        return renderDelimited(rows, ",");
    }

    private String renderDelimited(List<Map<String, Object>> rows, String separator) {
        if (rows.isEmpty()) return "";
        List<String> headers = new ArrayList<>(rows.getFirst().keySet());
        StringBuilder out = new StringBuilder(String.join(separator, headers)).append('\n');
        for (Map<String, Object> row : rows) {
            for (int i = 0; i < headers.size(); i++) {
                if (i > 0) out.append(separator);
                Object value = row.get(headers.get(i));
                out.append(value == null ? "" : String.valueOf(value).replace('\n', ' '));
            }
            out.append('\n');
        }
        return out.toString();
    }

    private byte[] encrypt(String requestId, byte[] clear) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(requestId), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(clear);
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return combined;
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("export artifact encrypt failed", ex);
        }
    }

    private SecretKeySpec key(String requestId) {
        byte[] digest = sha256Bytes(KEY_CONTEXT, requestId.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }

    private CheckedCreate check(ExportCreateCommand command) {
        if (command == null) throw failure("EXPORT_REQUEST_REQUIRED", "导出请求不能为空");
        String format = requireText(command.format(), "EXPORT_FORMAT_REQUIRED", 16).toUpperCase(Locale.ROOT);
        if (!FORMATS.contains(format)) throw failure("EXPORT_FORMAT_UNSUPPORTED", "导出格式不支持");
        String type = requireText(command.exportType(), "EXPORT_TYPE_REQUIRED", 64).toUpperCase(Locale.ROOT);
        if (!TYPES.contains(type)) throw failure("EXPORT_TYPE_UNSUPPORTED", "导出类型不支持");
        String requestId = text(command.requestId()) == null ? "EXP-" + UUID.randomUUID() : requireText(command.requestId(), "EXPORT_REQUEST_ID_REQUIRED", 64);
        String actor = requireText(command.actor(), "EXPORT_ACTOR_REQUIRED", 64);
        List<Map<String, Object>> rows = command.rows() == null ? List.of() : command.rows();
        List<Map<String, Object>> masked = rows.stream().map(this::maskProtectedValues).toList();
        return new CheckedCreate(requestId, command.tenantId(), type,
                requireText(command.producer(), "EXPORT_PRODUCER_REQUIRED", 64),
                requireText(command.jobName(), "EXPORT_JOB_NAME_REQUIRED", 128),
                actor, format, command.filters() == null ? Map.of() : command.filters(),
                command.ordering() == null ? List.of("id ASC") : command.ordering(),
                command.permission() == null ? "secure-async-export:create" : command.permission(),
                command.maskedFields() == null ? List.of("mobile", "phone") : command.maskedFields(), masked);
    }

    private Map<String, Object> maskProtectedValues(Map<String, Object> source) {
        Map<String, Object> row = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String lower = key.toLowerCase(Locale.ROOT);
            if (value instanceof String text && (lower.contains("mobile") || lower.contains("phone"))) {
                row.put(key, mask(text));
            } else {
                row.put(key, value);
            }
        });
        return row;
    }

    private String mask(String value) {
        String digits = value.replaceAll("\\D", "");
        if (digits.length() >= 7) {
            return digits.substring(0, 3) + "****" + digits.substring(digits.length() - 4);
        }
        return "已保护";
    }

    private ExportJob byRequestId(String requestId) {
        return jdbc.query("SELECT * FROM export_tasks WHERE request_id=?", mapper(), requestId).stream()
                .findFirst()
                .orElseThrow(() -> failure("EXPORT_JOB_NOT_FOUND", "导出任务不存在"));
    }

    public ExportJob byId(long id) {
        return jdbc.query("SELECT * FROM export_tasks WHERE id=?", mapper(), id).stream()
                .findFirst()
                .orElseThrow(() -> failure("EXPORT_JOB_NOT_FOUND", "导出任务不存在"));
    }

    private RowMapper<ExportJob> mapper() {
        return (rs, row) -> new ExportJob(
                rs.getLong("id"), rs.getString("request_id"), nullableLong(rs, "tenant_id"),
                rs.getString("export_type"), rs.getString("producer"), rs.getString("job_name"),
                rs.getString("created_by"), rs.getString("file_format"), rs.getString("status"),
                rs.getInt("progress_pct"), rs.getLong("record_count"),
                nullableLong(rs, "file_size_bytes"), rs.getString("file_sha256"),
                rs.getString("encryption_state"), rs.getInt("retry_count"), rs.getInt("split_count"),
                rs.getInt("partial_failure_count"), rs.getString("failure_reason"),
                rs.getString("authorization_snapshot"), rs.getString("source_snapshot"),
                rs.getString("artifact_manifest"), rs.getBytes("artifact_ciphertext"),
                timestamp(rs.getTimestamp("expires_at")), timestamp(rs.getTimestamp("created_at")),
                timestamp(rs.getTimestamp("completed_at")));
    }

    private void auditDownloadBoundary(Long actor, long exportId, Long tenantId, String operation, String result) {
        if (audits == null) return;
        audits.append(new OperationAuditService.AuditCommand(actor, operation, "EXPORT_TASK", String.valueOf(exportId),
                "POST", "/api/v1/console/exports", json(Map.of("exportTaskId", exportId)), result, 200,
                "system", null, 0));
    }

    private String fileName(ExportJob job) {
        return job.exportType().toLowerCase(Locale.ROOT) + "-" + job.id() + "." + job.format().toLowerCase(Locale.ROOT);
    }

    private String json(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("导出快照序列化失败", ex);
        }
    }

    private static String requireText(String value, String code, int max) {
        String text = text(value);
        if (text == null) throw failure(code, "导出字段不能为空");
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void setNullableLong(PreparedStatement ps, int index, Long value) throws java.sql.SQLException {
        if (value == null) ps.setNull(index, java.sql.Types.BIGINT);
        else ps.setLong(index, value);
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime timestamp(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private static Long actorLong(String actor) {
        try {
            return actor == null ? null : Long.parseLong(actor);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String digest(String value) {
        return digest(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String digest(byte[] value) {
        return HexFormat.of().formatHex(sha256Bytes(value));
    }

    private static byte[] sha256Bytes(byte[]... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (byte[] value : values) digest.update(value);
            return digest.digest();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static BusinessException failure(String code, String message) {
        return new BusinessException(code, message);
    }

    private record CheckedCreate(String requestId, Long tenantId, String exportType, String producer,
                                 String jobName, String actor, String format, Map<String, Object> filters,
                                 List<String> ordering, String permission, List<String> maskedFields,
                                 List<Map<String, Object>> rows) { }

    private record Artifact(String status, int progress, int splitCount, int partialFailureCount,
                            String failureReason, String manifest, byte[] ciphertext, String sha256,
                            boolean completed) { }

    public record ExportCreateCommand(String requestId, Long tenantId, String exportType, String producer,
                                      String jobName, String actor, String format, Map<String, Object> filters,
                                      List<String> ordering, String permission, List<String> maskedFields,
                                      List<Map<String, Object>> rows) { }

    public record RetryCommand(String actor, String reason) { }

    public record DownloadCommand(String actor, Long actorTenantId) { }

    public record ExportSearch(Long tenantId, String exportType, String status) { }

    public record ExportJob(long id, String requestId, Long tenantId, String exportType, String producer,
                            String jobName, String createdBy, String format, String status, int progress,
                            long recordCount, Long fileSizeBytes, String fileSha256, String encryptionState,
                            int retryCount, int splitCount, int partialFailureCount, String failureReason,
                            String authorizationSnapshot, String sourceSnapshot, String artifactManifest,
                            byte[] artifactCiphertext, LocalDateTime expiresAt, LocalDateTime createdAt,
                            LocalDateTime completedAt) { }

    public record DownloadArtifact(long id, String requestId, String fileName, String format,
                                   String encryptionState, String artifactBase64, String sha256,
                                   Long sizeBytes) { }
}
