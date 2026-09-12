package com.ycsopen.sms.core.service.tool;

import com.ycsopen.sms.core.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Phase 19: versioned prefix attribution and protected portability fallback. */
@Service
public class NumberAttributionService {
    private static final Pattern MOBILE = Pattern.compile("1[3-9][0-9]{9}");
    private static final Pattern PREFIX = Pattern.compile("[0-9]{3,7}");
    private static final Set<String> CARRIERS = Set.of("MOBILE", "UNICOM", "TELECOM", "VIRTUAL", "INTERNATIONAL", "UNKNOWN");
    private static final Set<String> UPDATE_TYPES = Set.of("FULL", "INCREMENTAL");
    private static final Set<String> FALLBACK_POLICIES = Set.of("PREFIX_ONLY", "CACHE_THEN_PREFIX");

    private final JdbcTemplate jdbc;
    private final PortabilityProvider provider;

    @Autowired
    public NumberAttributionService(JdbcTemplate jdbc) {
        this(jdbc, mobile -> null);
    }

    NumberAttributionService(JdbcTemplate jdbc, PortabilityProvider provider) {
        this.jdbc = jdbc;
        this.provider = provider;
    }

    @Transactional
    public PrefixImportResponse importPrefixes(PrefixImportRequest request, String actor) {
        if (request == null || request.rows() == null || request.rows().isEmpty()) {
            throw failure("PREFIX_IMPORT_REQUIRED", "号段导入不能为空");
        }
        if (request.rows().size() > 2_000) {
            throw failure("PREFIX_IMPORT_TOO_LARGE", "单次最多导入2000条号段");
        }
        String updateType = enumValue(request.updateType(), UPDATE_TYPES, "PREFIX_UPDATE_TYPE_INVALID");
        String source = text(request.sourceName(), "PREFIX_SOURCE_REQUIRED", 64);
        String versionNo = request.versionNo() == null || request.versionNo().isBlank()
                ? "PFX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT)
                : text(request.versionNo(), "PREFIX_VERSION_INVALID", 32);
        if ("FULL".equals(updateType)) {
            jdbc.update("UPDATE number_prefix_versions SET status='SUPERSEDED' WHERE status='ACTIVE'");
            jdbc.update("UPDATE number_prefix_mappings SET status='SUPERSEDED' WHERE status='ACTIVE'");
        }
        jdbc.update("""
                INSERT INTO number_prefix_versions(version_no, update_type, status, source_name, total_rows, conflict_count, actor, activated_at)
                VALUES (?,?,?,?,?,?,?,?)
                """, versionNo, updateType, "ACTIVE", source, request.rows().size(), 0, actor(actor), LocalDateTime.now());
        Long versionId = jdbc.queryForObject("SELECT id FROM number_prefix_versions WHERE version_no=?", Long.class, versionNo);
        if (versionId == null) {
            throw failure("PREFIX_VERSION_CREATE_FAILED", "号段版本创建失败");
        }
        int success = 0;
        List<String> errors = new ArrayList<>();
        for (PrefixRow row : request.rows()) {
            try {
                String prefix = prefix(row.prefix());
                String carrier = enumValue(row.carrier(), CARRIERS, "PREFIX_CARRIER_INVALID");
                String province = text(row.province(), "PREFIX_PROVINCE_REQUIRED", 32);
                String city = text(row.city(), "PREFIX_CITY_REQUIRED", 32);
                jdbc.update("""
                        INSERT INTO number_prefix_mappings(version_id, prefix, carrier, province, city, source_name, status)
                        VALUES (?,?,?,?,?,?,?)
                        """, versionId, prefix, carrier, province, city, source, "ACTIVE");
                success += 1;
            } catch (RuntimeException failure) {
                errors.add((row == null ? "" : row.prefix()) + ":" + failure.getMessage());
            }
        }
        jdbc.update("UPDATE number_prefix_versions SET conflict_count=? WHERE id=?", errors.size(), versionId);
        return new PrefixImportResponse(versionNo, success, errors.size(), errors);
    }

    @Transactional(readOnly = true)
    public AttributionResult lookup(String mobile, boolean forceProviderFailure) {
        String normalized = mobile(mobile);
        PrefixMapping prefix = longestPrefix(normalized);
        PortabilityRecord portability = null;
        if (!forceProviderFailure) {
            portability = provider.lookup(normalized);
        }
        if (portability == null) {
            portability = cachedPortability(normalized);
        }
        if (portability != null && portability.freshnessExpiresAt().isAfter(LocalDateTime.now())) {
            return new AttributionResult(normalized, portability.currentCarrier(), prefix == null ? "UNKNOWN" : prefix.carrier(),
                    prefix == null ? "未知" : prefix.province(), prefix == null ? "未知" : prefix.city(),
                    "PORTABILITY_CACHE", portability.sourceName(), portability.freshnessExpiresAt(), false);
        }
        if (prefix == null) {
            return new AttributionResult(normalized, "UNKNOWN", "UNKNOWN", "未知", "未知",
                    "PREFIX_FALLBACK_DEGRADED", "NO_PREFIX", null, forceProviderFailure);
        }
        return new AttributionResult(normalized, prefix.carrier(), prefix.carrier(), prefix.province(), prefix.city(),
                forceProviderFailure ? "PREFIX_FALLBACK_DEGRADED" : "PREFIX", prefix.sourceName(), null, forceProviderFailure);
    }

    @Transactional
    public PortabilityRow savePortability(PortabilityRequest request, String actor) {
        if (request == null) {
            throw failure("PORTABILITY_REQUEST_REQUIRED", "携号转网记录不能为空");
        }
        String mobile = mobile(request.mobile());
        String original = enumValue(request.originalCarrier(), CARRIERS, "PORTABILITY_ORIGINAL_CARRIER_INVALID");
        String current = enumValue(request.currentCarrier(), CARRIERS, "PORTABILITY_CURRENT_CARRIER_INVALID");
        String source = text(request.sourceName(), "PORTABILITY_SOURCE_REQUIRED", 64);
        int freshness = request.freshnessSeconds() == null ? 86_400 : bounded(request.freshnessSeconds(), 60, 2_592_000,
                "PORTABILITY_FRESHNESS_INVALID");
        LocalDateTime freshnessExpiresAt = LocalDateTime.now().plusSeconds(freshness);
        jdbc.update("""
                INSERT INTO mobile_portability(mobile_hash, masked_mobile, original_carrier, current_carrier, ported_at, source_name, freshness_expires_at, status)
                VALUES (?,?,?,?,?,?,?,'ACTIVE')
                ON DUPLICATE KEY UPDATE original_carrier=VALUES(original_carrier), current_carrier=VALUES(current_carrier),
                    ported_at=VALUES(ported_at), source_name=VALUES(source_name), freshness_expires_at=VALUES(freshness_expires_at),
                    status='ACTIVE'
                """, mobileHash(mobile), mask(mobile), original, current, request.portedAt(), source, freshnessExpiresAt);
        return portabilityRows().stream().filter(row -> row.maskedMobile().equals(mask(mobile))).findFirst()
                .orElseThrow(() -> failure("PORTABILITY_SAVE_FAILED", "携号转网记录保存失败"));
    }

    @Transactional(readOnly = true)
    public List<PrefixVersionRow> versions() {
        return jdbc.query("""
                SELECT id, version_no, update_type, status, source_name, total_rows, conflict_count, actor, created_at, activated_at
                FROM number_prefix_versions
                ORDER BY created_at DESC, id DESC
                LIMIT 20
                """, (rs, row) -> new PrefixVersionRow(rs.getLong("id"), rs.getString("version_no"),
                rs.getString("update_type"), rs.getString("status"), rs.getString("source_name"),
                rs.getInt("total_rows"), rs.getInt("conflict_count"), rs.getString("actor"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("activated_at") == null ? null : rs.getTimestamp("activated_at").toLocalDateTime()));
    }

    @Transactional(readOnly = true)
    public List<PortabilityRow> portabilityRows() {
        return jdbc.query("""
                SELECT id, masked_mobile, original_carrier, current_carrier, ported_at, source_name, freshness_expires_at, status, updated_at
                FROM mobile_portability
                ORDER BY updated_at DESC, id DESC
                LIMIT 100
                """, (rs, row) -> new PortabilityRow(rs.getLong("id"), rs.getString("masked_mobile"),
                rs.getString("original_carrier"), rs.getString("current_carrier"),
                rs.getDate("ported_at") == null ? null : rs.getDate("ported_at").toLocalDate(),
                rs.getString("source_name"), rs.getTimestamp("freshness_expires_at").toLocalDateTime(),
                rs.getString("status"), rs.getTimestamp("updated_at").toLocalDateTime()));
    }

    private PrefixMapping longestPrefix(String mobile) {
        for (int length = 7; length >= 3; length--) {
            String prefix = mobile.substring(0, length);
            List<PrefixMapping> rows = jdbc.query("""
                    SELECT prefix, carrier, province, city, source_name
                    FROM number_prefix_mappings
                    WHERE status='ACTIVE' AND prefix=?
                    ORDER BY id DESC
                    LIMIT 1
                    """, (rs, row) -> new PrefixMapping(rs.getString("prefix"), rs.getString("carrier"),
                    rs.getString("province"), rs.getString("city"), rs.getString("source_name")), prefix);
            if (!rows.isEmpty()) return rows.get(0);
        }
        return null;
    }

    private PortabilityRecord cachedPortability(String mobile) {
        List<PortabilityRecord> rows = jdbc.query("""
                SELECT current_carrier, source_name, freshness_expires_at
                FROM mobile_portability
                WHERE mobile_hash=? AND status='ACTIVE'
                ORDER BY updated_at DESC
                LIMIT 1
                """, (rs, row) -> new PortabilityRecord(rs.getString("current_carrier"),
                rs.getString("source_name"), rs.getTimestamp("freshness_expires_at").toLocalDateTime()), mobileHash(mobile));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String mobile(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\D", "");
        if (!MOBILE.matcher(normalized).matches()) {
            throw failure("MOBILE_INVALID", "手机号必须为有效的11位国内号码");
        }
        return normalized;
    }

    private String prefix(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!PREFIX.matcher(normalized).matches()) {
            throw failure("PREFIX_INVALID", "号段必须为3到7位数字");
        }
        return normalized;
    }

    private int bounded(Integer value, int min, int max, String code) {
        if (value == null || value < min || value > max) {
            throw failure(code, "数值超出允许范围");
        }
        return value;
    }

    private String enumValue(String value, Set<String> allowed, String code) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw failure(code, "枚举值非法：" + value);
        }
        return normalized;
    }

    private String text(String value, String code, int maxLength) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty() || trimmed.length() > maxLength) {
            throw failure(code, "文本不能为空且不能超过长度限制");
        }
        return trimmed;
    }

    private String actor(String value) {
        return text(value, "ACTOR_REQUIRED", 64);
    }

    private String mobileHash(String mobile) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(mobile.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    private String mask(String mobile) {
        return mobile.substring(0, 3) + "****" + mobile.substring(7);
    }

    private BusinessException failure(String code, String message) {
        return new BusinessException(code, message);
    }

    public interface PortabilityProvider {
        PortabilityRecord lookup(String mobile);
    }

    private record PrefixMapping(String prefix, String carrier, String province, String city, String sourceName) { }
    public record PortabilityRecord(String currentCarrier, String sourceName, LocalDateTime freshnessExpiresAt) { }
    public record PrefixRow(String prefix, String carrier, String province, String city) { }
    public record PrefixImportRequest(String versionNo, String updateType, String sourceName, List<PrefixRow> rows) { }
    public record PrefixImportResponse(String versionNo, int success, int failed, List<String> errors) { }
    public record AttributionResult(String mobile, String carrier, String prefixCarrier, String province, String city,
                                    String source, String sourceName, LocalDateTime freshnessExpiresAt,
                                    boolean providerFailure) { }
    public record PortabilityRequest(String mobile, String originalCarrier, String currentCarrier, LocalDate portedAt,
                                     String sourceName, Integer freshnessSeconds) { }
    public record PortabilityRow(Long id, String maskedMobile, String originalCarrier, String currentCarrier,
                                 LocalDate portedAt, String sourceName, LocalDateTime freshnessExpiresAt,
                                 String status, LocalDateTime updatedAt) { }
    public record PrefixVersionRow(Long id, String versionNo, String updateType, String status, String sourceName,
                                   int totalRows, int conflictCount, String actor,
                                   LocalDateTime createdAt, LocalDateTime activatedAt) { }
}
