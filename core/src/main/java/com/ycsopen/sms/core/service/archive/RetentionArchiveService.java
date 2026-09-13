package com.ycsopen.sms.core.service.archive;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.service.export.SecureAsyncExportService;
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
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Phase 47: policy-driven encrypted archive manifest, checksum, search, restore and export boundary. */
@Service
public class RetentionArchiveService {
    private static final byte[] KEY_CONTEXT = "ycsopen-sms-retention-archive-restore-v1".getBytes(StandardCharsets.UTF_8);
    private static final TypeReference<List<Map<String, Object>>> ROWS = new TypeReference<>() { };
    private static final Map<String, String> DEFAULT_TABLES = Map.of(
            "MESSAGE_TASKS", "message_tasks",
            "DELIVERY_REPORTS", "delivery_reports",
            "UPLINK_RECORDS", "uplink_records",
            "UNSUBSCRIBE_RECORDS", "unsubscribe_records",
            "BULK_SENDINGS", "bulk_sendings",
            "PRIVILEGED_OPERATION_AUDITS", "privileged_operation_audits",
            "COMPLAINTS", "complaints",
            "BALANCE_AUDITS", "balance_audit_entries");

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final SecureAsyncExportService exports;
    private final SecureRandom random = new SecureRandom();

    public RetentionArchiveService(JdbcTemplate jdbc, ObjectMapper json, SecureAsyncExportService exports) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.json = Objects.requireNonNull(json);
        this.exports = exports;
    }

    @Transactional(readOnly = true)
    public List<ArchivePolicy> policies() {
        return jdbc.query("""
                SELECT id, data_domain, source_table, retention_days, hot_months, partition_unit,
                       legal_hold_until, encryption_required, status, updated_by, updated_at
                FROM archive_policies
                ORDER BY data_domain
                """, policyMapper());
    }

    @Transactional
    public ArchivePolicy savePolicy(PolicyCommand command) {
        String domain = domain(command.dataDomain());
        int retentionDays = Math.max(730, command.retentionDays() == null ? 730 : command.retentionDays());
        int hotMonths = Math.max(1, command.hotMonths() == null ? 3 : command.hotMonths());
        String sourceTable = DEFAULT_TABLES.getOrDefault(domain, domain.toLowerCase(Locale.ROOT));
        List<Long> ids = jdbc.query("SELECT id FROM archive_policies WHERE data_domain=?",
                (row, index) -> row.getLong("id"), domain);
        if (ids.isEmpty()) {
            jdbc.update("""
                    INSERT INTO archive_policies
                        (data_domain, source_table, retention_days, hot_months, partition_unit,
                         legal_hold_until, encryption_required, status, updated_by)
                    VALUES (?, ?, ?, ?, 'MONTH', ?, 1, 'ACTIVE', ?)
                    """, domain, sourceTable, retentionDays, hotMonths, timestamp(command.legalHoldUntil()), text(command.actor()));
        } else {
            jdbc.update("""
                    UPDATE archive_policies
                       SET retention_days=?, hot_months=?, legal_hold_until=?, updated_by=?, updated_at=CURRENT_TIMESTAMP
                     WHERE data_domain=?
                    """, retentionDays, hotMonths, timestamp(command.legalHoldUntil()), text(command.actor()), domain);
        }
        return policy(domain);
    }

    @Transactional
    public ArchiveManifest archiveEligible(ScanCommand command) {
        ArchivePolicy policy = ensurePolicy(command.dataDomain(), command.actor());
        List<Map<String, Object>> rows = eligibleRows(policy, command.tenantId());
        YearMonth partition = YearMonth.now().minusMonths(policy.hotMonths());
        return archive(new ArchiveCommand(policy.dataDomain(), command.tenantId(), partition.toString(),
                rows, false, command.actor()));
    }

    @Transactional
    public ArchiveManifest archive(ArchiveCommand command) {
        ArchivePolicy policy = ensurePolicy(command.dataDomain(), command.actor());
        List<Map<String, Object>> rows = command.rows() == null ? List.of() : command.rows();
        String rowsJson = json(rows);
        String checksum = digest(rowsJson);
        byte[] ciphertext = encrypt(policy.dataDomain() + ":" + checksum, rowsJson.getBytes(StandardCharsets.UTF_8));
        LocalDateTime retentionUntil = LocalDateTime.now().plusDays(policy.retentionDays());
        boolean forcedFailure = Boolean.TRUE.equals(command.forceFailure());
        boolean legalHold = policy.legalHoldUntil() != null && policy.legalHoldUntil().isAfter(LocalDateTime.now());
        String sourceIdentity = json(Map.of(
                "sourceTable", policy.sourceTable(),
                "ids", ids(rows),
                "businessKeys", businessKeys(rows),
                "sourceDigest", checksum));
        String manifestJson = json(Map.of(
                "dataDomain", policy.dataDomain(),
                "partitionKey", partitionKey(command.partitionKey()),
                "rowCount", rows.size(),
                "retentionDays", policy.retentionDays(),
                "hotMonths", policy.hotMonths(),
                "checksumSha256", checksum,
                "encryption", "AES/GCM archive-v1",
                "legalHold", legalHold,
                "sourceTable", policy.sourceTable()));
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO archive_manifests
                        (policy_id, data_domain, source_table, partition_key, tenant_id, archive_status,
                         row_count, source_identity_json, manifest_json, archive_ciphertext, checksum_sha256,
                         encryption_key_version, retention_until, legal_hold_until, deletion_eligible,
                         failure_reason, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'archive-v1', ?, ?, ?, ?, ?)
                    """, new String[] {"id"});
            ps.setLong(1, policy.id());
            ps.setString(2, policy.dataDomain());
            ps.setString(3, policy.sourceTable());
            ps.setString(4, partitionKey(command.partitionKey()));
            setNullableLong(ps, 5, command.tenantId());
            ps.setString(6, forcedFailure ? "FAILED" : "COMPLETED");
            ps.setLong(7, rows.size());
            ps.setString(8, sourceIdentity);
            ps.setString(9, manifestJson);
            ps.setBytes(10, ciphertext);
            ps.setString(11, checksum);
            ps.setTimestamp(12, Timestamp.valueOf(retentionUntil));
            ps.setTimestamp(13, timestamp(policy.legalHoldUntil()));
            ps.setBoolean(14, false);
            ps.setString(15, forcedFailure ? "测试注入的归档失败" : null);
            ps.setString(16, text(command.actor()));
            return ps;
        }, keys);
        Number id = keys.getKey();
        if (id == null) throw new IllegalStateException("archive manifest key was not returned");
        return byId(id.longValue());
    }

    @Transactional(readOnly = true)
    public List<ArchiveManifest> search(ArchiveSearch search) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (search != null && text(search.dataDomain()) != null) {
            where.append(" AND data_domain=?");
            params.add(domain(search.dataDomain()));
        }
        if (search != null && text(search.status()) != null) {
            where.append(" AND archive_status=?");
            params.add(text(search.status()).toUpperCase(Locale.ROOT));
        }
        if (search != null && search.tenantId() != null) {
            where.append(" AND tenant_id=?");
            params.add(search.tenantId());
        }
        params.add(200);
        return jdbc.query("""
                SELECT * FROM archive_manifests
                """ + where + " ORDER BY created_at DESC, id DESC LIMIT ?", manifestMapper(), params.toArray());
    }

    @Transactional
    public ArchiveManifest verify(long id) {
        ArchiveManifest manifest = byId(id);
        try {
            String clear = decrypt(manifest);
            String checksum = digest(clear);
            if (!checksum.equals(manifest.checksumSha256())) {
                jdbc.update("""
                        UPDATE archive_manifests
                           SET archive_status='CORRUPTED', failure_reason='归档校验和不匹配', verified_at=CURRENT_TIMESTAMP
                         WHERE id=?
                        """, id);
                return byId(id);
            }
            jdbc.update("""
                    UPDATE archive_manifests
                       SET verified_at=CURRENT_TIMESTAMP,
                           deletion_eligible=CASE WHEN retention_until < CURRENT_TIMESTAMP
                                AND (legal_hold_until IS NULL OR legal_hold_until < CURRENT_TIMESTAMP)
                                THEN 1 ELSE 0 END
                     WHERE id=?
                    """, id);
            return byId(id);
        } catch (RuntimeException ex) {
            jdbc.update("""
                    UPDATE archive_manifests
                       SET archive_status='CORRUPTED', failure_reason='归档密文无法解密', verified_at=CURRENT_TIMESTAMP
                     WHERE id=?
                    """, id);
            return byId(id);
        }
    }

    @Transactional
    public RestoreJob restore(long id, RestoreCommand command) {
        ArchiveManifest verified = verify(id);
        if (!List.of("COMPLETED", "RESTORED").contains(verified.archiveStatus())) {
            return restoreJob(id, "RESTORE", "FAILED", command.actor(), "归档不可恢复: " + verified.failureReason(),
                    0, null);
        }
        List<Map<String, Object>> rows = decryptRows(verified);
        jdbc.update("UPDATE archive_manifests SET archive_status='RESTORED', restored_at=CURRENT_TIMESTAMP WHERE id=?",
                id);
        return restoreJob(id, "RESTORE", "COMPLETED", command.actor(), "已校验并恢复归档记录", rows.size(), null);
    }

    @Transactional
    public RestoreJob export(long id, RestoreCommand command) {
        ArchiveManifest verified = verify(id);
        if (!List.of("COMPLETED", "RESTORED").contains(verified.archiveStatus())) {
            return restoreJob(id, "EXPORT", "FAILED", command.actor(), "归档不可导出: " + verified.failureReason(),
                    0, null);
        }
        List<Map<String, Object>> rows = decryptRows(verified);
        Long exportTaskId = null;
        if (exports != null) {
            SecureAsyncExportService.ExportJob job = exports.create(new SecureAsyncExportService.ExportCreateCommand(
                    "ARCHIVE-" + id + "-" + UUID.randomUUID(), verified.tenantId(), "ARCHIVE_RESTORE",
                    "RETENTION_ARCHIVE", "归档恢复导出", command.actor(), "CSV",
                    Map.of("archiveManifestId", id, "partitionKey", verified.partitionKey()),
                    List.of("archive id ASC"), "retention-archive:export", List.of("mobile", "phone"), rows));
            exportTaskId = job.id();
            jdbc.update("UPDATE archive_manifests SET exported_task_id=? WHERE id=?", exportTaskId, id);
        }
        return restoreJob(id, "EXPORT", "COMPLETED", command.actor(), "已校验并创建归档导出任务",
                rows.size(), exportTaskId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> decryptRowsForVerification(long id) {
        return decryptRows(byId(id));
    }

    private RestoreJob restoreJob(long manifestId, String requestType, String status, String actor,
                                  String message, long count, Long exportTaskId) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO archive_restore_jobs
                        (manifest_id, request_type, status, requested_by, result_message,
                         restored_record_count, export_task_id, completed_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """, new String[] {"id"});
            ps.setLong(1, manifestId);
            ps.setString(2, requestType);
            ps.setString(3, status);
            ps.setString(4, text(actor));
            ps.setString(5, message);
            ps.setLong(6, count);
            setNullableLong(ps, 7, exportTaskId);
            return ps;
        }, keys);
        Number id = keys.getKey();
        if (id == null) throw new IllegalStateException("archive restore job key was not returned");
        return jdbc.queryForObject("""
                SELECT id, manifest_id, request_type, status, requested_by, result_message,
                       restored_record_count, export_task_id, created_at, completed_at
                FROM archive_restore_jobs WHERE id=?
                """, restoreMapper(), id.longValue());
    }

    private ArchivePolicy ensurePolicy(String dataDomain, String actor) {
        String domain = domain(dataDomain);
        List<ArchivePolicy> found = jdbc.query("""
                SELECT id, data_domain, source_table, retention_days, hot_months, partition_unit,
                       legal_hold_until, encryption_required, status, updated_by, updated_at
                FROM archive_policies WHERE data_domain=?
                """, policyMapper(), domain);
        if (!found.isEmpty()) return found.getFirst();
        return savePolicy(new PolicyCommand(domain, 730, 3, null, actor));
    }

    private List<Map<String, Object>> eligibleRows(ArchivePolicy policy, Long tenantId) {
        Timestamp cutoff = Timestamp.valueOf(LocalDateTime.now().minusMonths(policy.hotMonths()));
        return switch (policy.dataDomain()) {
            case "MESSAGE_TASKS" -> queryEligible("""
                    SELECT id, message_id, tenant_id, send_status, cost, created_at
                    FROM message_tasks
                    WHERE created_at < ?
                    """, " AND tenant_id=?", " ORDER BY created_at ASC LIMIT 500", cutoff, tenantId);
            case "DELIVERY_REPORTS" -> queryEligible("""
                    SELECT id, message_id, report_status, error_code, report_time
                    FROM delivery_reports
                    WHERE report_time < ?
                    """, "", " ORDER BY report_time ASC LIMIT 500", cutoff, null);
            case "UPLINK_RECORDS" -> queryEligible("""
                    SELECT id, tenant_id, target_number, is_unsubscribe, push_status, received_at
                    FROM uplink_records
                    WHERE received_at < ?
                    """, " AND tenant_id=?", " ORDER BY received_at ASC LIMIT 500", cutoff, tenantId);
            case "UNSUBSCRIBE_RECORDS" -> queryEligible("""
                    SELECT id, tenant_id, mobile_hash, trigger_keyword, result, unsubscribed_at
                    FROM unsubscribe_records
                    WHERE unsubscribed_at < ?
                    """, " AND tenant_id=?", " ORDER BY unsubscribed_at ASC LIMIT 500", cutoff, tenantId);
            case "BULK_SENDINGS" -> queryEligible("""
                    SELECT id, tenant_id, task_name, task_status, total_count, created_at
                    FROM bulk_sendings
                    WHERE created_at < ?
                    """, " AND tenant_id=?", " ORDER BY created_at ASC LIMIT 500", cutoff, tenantId);
            case "PRIVILEGED_OPERATION_AUDITS" -> queryEligible("""
                    SELECT id, actor_username, tenant_id, operation, result_code, occurred_at
                    FROM privileged_operation_audits
                    WHERE occurred_at < ?
                    """, " AND tenant_id=?", " ORDER BY occurred_at ASC LIMIT 500", cutoff, tenantId);
            case "COMPLAINTS" -> queryEligible("""
                    SELECT id, tenant_id, message_id, source, status, created_at
                    FROM complaints
                    WHERE created_at < ?
                    """, " AND tenant_id=?", " ORDER BY created_at ASC LIMIT 500", cutoff, tenantId);
            case "BALANCE_AUDITS" -> queryEligible("""
                    SELECT id, tenant_id, business_doc_id, mutation_type, amount_mil, created_at
                    FROM balance_audit_entries
                    WHERE created_at < ?
                    """, " AND tenant_id=?", " ORDER BY created_at ASC LIMIT 500", cutoff, tenantId);
            default -> List.of();
        };
    }

    private List<Map<String, Object>> queryEligible(String baseSql, String tenantSql, String suffix,
                                                    Timestamp cutoff, Long tenantId) {
        if (tenantId == null || tenantSql.isBlank()) {
            return jdbc.queryForList(baseSql + suffix, cutoff);
        }
        return jdbc.queryForList(baseSql + tenantSql + suffix, cutoff, tenantId);
    }

    private ArchivePolicy policy(String domain) {
        return jdbc.queryForObject("""
                SELECT id, data_domain, source_table, retention_days, hot_months, partition_unit,
                       legal_hold_until, encryption_required, status, updated_by, updated_at
                FROM archive_policies WHERE data_domain=?
                """, policyMapper(), domain);
    }

    private ArchiveManifest byId(long id) {
        List<ArchiveManifest> manifests = jdbc.query("SELECT * FROM archive_manifests WHERE id=?",
                manifestMapper(), id);
        if (manifests.isEmpty()) {
            throw new BusinessException("ARCHIVE_MANIFEST_NOT_FOUND", "归档清单不存在");
        }
        return manifests.getFirst();
    }

    private String decrypt(ArchiveManifest manifest) {
        try {
            byte[] all = manifest.archiveCiphertext();
            byte[] iv = java.util.Arrays.copyOfRange(all, 0, 12);
            byte[] encrypted = java.util.Arrays.copyOfRange(all, 12, all.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(manifest.dataDomain() + ":" + manifest.checksumSha256()),
                    new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("archive decrypt failed", ex);
        }
    }

    private List<Map<String, Object>> decryptRows(ArchiveManifest manifest) {
        try {
            return json.readValue(decrypt(manifest), ROWS);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("archive row parse failed", ex);
        }
    }

    private byte[] encrypt(String context, byte[] clear) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(context), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(clear);
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return combined;
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("archive encrypt failed", ex);
        }
    }

    private SecretKeySpec key(String context) {
        return new SecretKeySpec(MessageDigestHolder.sha256(concat(KEY_CONTEXT, context.getBytes(StandardCharsets.UTF_8))),
                "AES");
    }

    private static byte[] concat(byte[] left, byte[] right) {
        byte[] out = new byte[left.length + right.length];
        System.arraycopy(left, 0, out, 0, left.length);
        System.arraycopy(right, 0, out, left.length, right.length);
        return out;
    }

    private String json(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("archive json serialization failed", ex);
        }
    }

    private static String digest(String value) {
        return HexFormat.of().formatHex(MessageDigestHolder.sha256(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static String domain(String value) {
        String text = text(value);
        if (text == null) throw new BusinessException("ARCHIVE_DOMAIN_REQUIRED", "归档数据域不能为空");
        return text.toUpperCase(Locale.ROOT);
    }

    private static String partitionKey(String value) {
        String text = text(value);
        return text == null ? YearMonth.now().toString() : text;
    }

    private static List<Object> ids(List<Map<String, Object>> rows) {
        List<Object> ids = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (row.containsKey("id")) ids.add(row.get("id"));
        }
        return ids;
    }

    private static List<Object> businessKeys(List<Map<String, Object>> rows) {
        List<Object> keys = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (row.containsKey("message_id")) keys.add(row.get("message_id"));
            else if (row.containsKey("request_id")) keys.add(row.get("request_id"));
            else if (row.containsKey("task_name")) keys.add(row.get("task_name"));
        }
        return keys;
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private static LocalDateTime timestamp(java.sql.ResultSet row, String column) throws java.sql.SQLException {
        Timestamp value = row.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private static Long nullableLong(java.sql.ResultSet row, String column) throws java.sql.SQLException {
        long value = row.getLong(column);
        return row.wasNull() ? null : value;
    }

    private static void setNullableLong(PreparedStatement statement, int index, Long value)
            throws java.sql.SQLException {
        if (value == null) statement.setNull(index, java.sql.Types.BIGINT);
        else statement.setLong(index, value);
    }

    private static RowMapper<ArchivePolicy> policyMapper() {
        return (row, index) -> new ArchivePolicy(row.getLong("id"), row.getString("data_domain"),
                row.getString("source_table"), row.getInt("retention_days"), row.getInt("hot_months"),
                row.getString("partition_unit"), timestamp(row, "legal_hold_until"),
                row.getBoolean("encryption_required"), row.getString("status"), row.getString("updated_by"),
                timestamp(row, "updated_at"));
    }

    private static RowMapper<ArchiveManifest> manifestMapper() {
        return (row, index) -> new ArchiveManifest(row.getLong("id"), row.getLong("policy_id"),
                row.getString("data_domain"), row.getString("source_table"), row.getString("partition_key"),
                nullableLong(row, "tenant_id"), row.getString("archive_status"), row.getLong("row_count"),
                row.getString("source_identity_json"), row.getString("manifest_json"),
                row.getBytes("archive_ciphertext"), row.getString("checksum_sha256"),
                row.getString("encryption_key_version"), timestamp(row, "retention_until"),
                timestamp(row, "legal_hold_until"), row.getBoolean("deletion_eligible"),
                row.getString("failure_reason"), row.getString("created_by"), timestamp(row, "created_at"),
                timestamp(row, "verified_at"), timestamp(row, "restored_at"), nullableLong(row, "exported_task_id"));
    }

    private static RowMapper<RestoreJob> restoreMapper() {
        return (row, index) -> new RestoreJob(row.getLong("id"), row.getLong("manifest_id"),
                row.getString("request_type"), row.getString("status"), row.getString("requested_by"),
                row.getString("result_message"), row.getLong("restored_record_count"),
                nullableLong(row, "export_task_id"), timestamp(row, "created_at"), timestamp(row, "completed_at"));
    }

    private static final class MessageDigestHolder {
        private static byte[] sha256(byte[] value) {
            try {
                return MessageDigest.getInstance("SHA-256").digest(value);
            } catch (GeneralSecurityException ex) {
                throw new IllegalStateException("sha256 unavailable", ex);
            }
        }
    }

    public record PolicyCommand(String dataDomain, Integer retentionDays, Integer hotMonths,
                                LocalDateTime legalHoldUntil, String actor) { }

    public record ScanCommand(String dataDomain, Long tenantId, String actor) { }

    public record ArchiveCommand(String dataDomain, Long tenantId, String partitionKey,
                                 List<Map<String, Object>> rows, Boolean forceFailure, String actor) { }

    public record RestoreCommand(String actor) { }

    public record ArchiveSearch(String dataDomain, String status, Long tenantId) { }

    public record ArchivePolicy(long id, String dataDomain, String sourceTable, int retentionDays, int hotMonths,
                                String partitionUnit, LocalDateTime legalHoldUntil, boolean encryptionRequired,
                                String status, String updatedBy, LocalDateTime updatedAt) { }

    public record ArchiveManifest(long id, long policyId, String dataDomain, String sourceTable, String partitionKey,
                                  Long tenantId, String archiveStatus, long rowCount, String sourceIdentityJson,
                                  String manifestJson, @JsonIgnore byte[] archiveCiphertext, String checksumSha256,
                                  String encryptionKeyVersion, LocalDateTime retentionUntil,
                                  LocalDateTime legalHoldUntil, boolean deletionEligible, String failureReason,
                                  String createdBy, LocalDateTime createdAt, LocalDateTime verifiedAt,
                                  LocalDateTime restoredAt, Long exportedTaskId) {
        @JsonIgnore
        public String archiveCiphertextBase64() {
            return Base64.getEncoder().encodeToString(archiveCiphertext);
        }
    }

    public record RestoreJob(long id, long manifestId, String requestType, String status, String requestedBy,
                             String resultMessage, long restoredRecordCount, Long exportTaskId,
                             LocalDateTime createdAt, LocalDateTime completedAt) { }
}
