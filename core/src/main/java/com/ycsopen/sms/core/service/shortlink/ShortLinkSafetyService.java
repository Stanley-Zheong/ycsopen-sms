package com.ycsopen.sms.core.service.shortlink;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Phase 48: short-link creation, safety review, public redirect fence and analytics. */
@Service
public class ShortLinkSafetyService {
    private static final String DEFAULT_SHORT_DOMAIN = "s.ycsopen.test";
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ShortLinkSafetyService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.json = Objects.requireNonNull(json);
    }

    @Transactional
    public ShortLinkRow create(CreateCommand command, String actor) {
        CreateCommand checked = command.checked();
        TargetCheck check = reviewTarget(checked.originalUrl(), checked.redirectChain(), checked.resolvedIps());
        String shortDomain = text(checked.customDomain()) == null ? DEFAULT_SHORT_DOMAIN : host(text(checked.customDomain()));
        DomainRecord shortDomainRecord = requireApprovedDomain(shortDomain, "SHORT");
        LocalDate validUntil = checked.validUntil() == null ? LocalDate.now().plusDays(30) : checked.validUntil();
        if (validUntil.isBefore(LocalDate.now()) || validUntil.isAfter(LocalDate.now().plusDays(365))) {
            throw new BusinessException("SHORTLINK_VALIDITY_INVALID", "短链有效期必须从今天起且不超过365天");
        }
        String code = nextCode();
        String shortUrl = "https://" + shortDomainRecord.domainName() + "/s/" + code;
        String targetSha = sha256(check.normalizedUrl());
        String autoJson = json(Map.of(
                "verdict", check.verdict(),
                "checks", check.checks(),
                "redirectChain", checked.redirectChain() == null ? List.of() : checked.redirectChain(),
                "resolvedIps", checked.resolvedIps() == null ? List.of() : checked.resolvedIps()));
        String domainJson = json(Map.of(
                "targetDomain", check.targetDomain().domainName(),
                "targetFilingStatus", check.targetDomain().filingStatus(),
                "targetDomainAgeDays", check.targetDomain().domainAgeDays(),
                "shortDomain", shortDomainRecord.domainName()));
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO short_links(tenant_id, target_url, custom_domain, short_code, short_url, valid_until,
                        status, target_version, immutable_target_sha256, automated_result_json,
                        screenshot_evidence_ref, domain_evidence_json, risk_level)
                    VALUES (?, ?, ?, ?, ?, ?, 'PENDING', 1, ?, ?, ?, ?, ?)
                    """, new String[] {"id"});
            ps.setLong(1, checked.tenantId());
            ps.setString(2, check.normalizedUrl());
            ps.setString(3, shortDomainRecord.domainName());
            ps.setString(4, code);
            ps.setString(5, shortUrl);
            ps.setObject(6, validUntil);
            ps.setString(7, targetSha);
            ps.setString(8, autoJson);
            ps.setString(9, "domain-evidence:" + check.targetDomain().domainName());
            ps.setString(10, domainJson);
            ps.setString(11, check.riskLevel());
            return ps;
        }, keys);
        long id = key(keys);
        audit(id, "AUTO_REVIEW", actor, check.verdict(), autoJson, check.riskLevel(), null);
        return requireLink(id);
    }

    @Transactional(readOnly = true)
    public List<ShortLinkRow> tenantLinks(long tenantId) {
        return jdbc.query("SELECT * FROM short_links WHERE tenant_id=? ORDER BY id DESC LIMIT 200", mapper(), tenantId);
    }

    @Transactional(readOnly = true)
    public List<ShortLinkRow> reviewQueue(String status) {
        String selected = text(status) == null ? "PENDING" : status.trim().toUpperCase(Locale.ROOT);
        return jdbc.query("SELECT * FROM short_links WHERE status=? ORDER BY id DESC LIMIT 200", mapper(), selected);
    }

    @Transactional
    public ShortLinkRow approve(long id, ReviewCommand command, String actor) {
        ShortLinkRow row = requireLink(id);
        if (!"PENDING".equals(row.status())) {
            throw new BusinessException("SHORTLINK_REVIEW_STATE_INVALID", "只有待审核短链可以批准");
        }
        if (!row.automatedResultJson().contains("\"verdict\":\"PASS\"")) {
            throw new BusinessException("SHORTLINK_AUTO_REVIEW_BLOCKED", "自动安全审核未通过，不能批准");
        }
        String opinion = requireText(command == null ? null : command.opinion(), "SHORTLINK_REVIEW_OPINION_REQUIRED", 255);
        jdbc.update("""
                UPDATE short_links
                   SET status='APPROVED', review_opinion=?, reviewed_by=?, reviewed_at=CURRENT_TIMESTAMP
                 WHERE id=?
                """, opinion, actor(actor), id);
        audit(id, "HUMAN_APPROVE", actor, "APPROVED", json(Map.of("opinion", opinion)), "LOW", opinion);
        return requireLink(id);
    }

    @Transactional
    public ShortLinkRow reject(long id, ReviewCommand command, String actor) {
        ShortLinkRow row = requireLink(id);
        if (!List.of("PENDING", "APPROVED").contains(row.status())) {
            throw new BusinessException("SHORTLINK_REVIEW_STATE_INVALID", "当前短链状态不能驳回");
        }
        String opinion = requireText(command == null ? null : command.opinion(), "SHORTLINK_REVIEW_OPINION_REQUIRED", 255);
        jdbc.update("""
                UPDATE short_links
                   SET status='REJECTED', review_opinion=?, reviewed_by=?, reviewed_at=CURRENT_TIMESTAMP
                 WHERE id=?
                """, opinion, actor(actor), id);
        audit(id, "HUMAN_REJECT", actor, "REJECTED", json(Map.of("opinion", opinion)), row.riskLevel(), opinion);
        return requireLink(id);
    }

    @Transactional
    public RedirectDecision redirect(String code, ClickCommand command) {
        List<ShortLinkRow> rows = jdbc.query("SELECT * FROM short_links WHERE short_code=?", mapper(), code);
        if (rows.isEmpty()) {
            return RedirectDecision.safe("PENDING", "短链不存在或尚未审核");
        }
        ShortLinkRow row = rows.getFirst();
        if ("APPROVED".equals(row.status()) && row.validUntil().isBefore(LocalDate.now())) {
            jdbc.update("UPDATE short_links SET status='EXPIRED' WHERE id=?", row.id());
            audit(row.id(), "EXPIRE", "system", "EXPIRED", json(Map.of("validUntil", row.validUntil().toString())), row.riskLevel(), "到期自动失效");
            row = requireLink(row.id());
        }
        if (!"APPROVED".equals(row.status())) {
            return RedirectDecision.safe(row.status(), safeMessage(row.status()));
        }
        recordClick(row.id(), command == null ? new ClickCommand(null, null, null) : command);
        return new RedirectDecision(true, row.targetUrl(), row.status(), "允许跳转");
    }

    @Transactional
    public ShortLinkRow inspect(long id, InspectCommand command, String actor) {
        ShortLinkRow row = requireLink(id);
        InspectCommand checked = command == null ? new InspectCommand(row.targetUrl(), List.of()) : command;
        TargetCheck check = reviewTarget(checked.observedTargetUrl(), List.of(checked.observedTargetUrl()), checked.resolvedIps());
        boolean drifted = !"APPROVED".equals(row.status()) || !sha256(check.normalizedUrl()).equals(row.immutableTargetSha256())
                || !"PASS".equals(check.verdict());
        jdbc.update("UPDATE short_links SET last_recheck_at=CURRENT_TIMESTAMP WHERE id=?", id);
        if (drifted) {
            String reason = "目标漂移或风险升高";
            jdbc.update("UPDATE short_links SET status='OFFLINE', offline_reason=?, offline_at=CURRENT_TIMESTAMP WHERE id=?",
                    reason, id);
            audit(id, "TARGET_OFFLINE_ALERT", actor, "OFFLINE", json(Map.of(
                    "observedTargetUrl", check.normalizedUrl(),
                    "checks", check.checks(),
                    "alert", "short-link-offline")), "HIGH", reason);
        } else {
            audit(id, "TARGET_RECHECK", actor, "APPROVED", json(Map.of("checks", check.checks())), row.riskLevel(), "巡检通过");
        }
        return requireLink(id);
    }

    @Transactional(readOnly = true)
    public Analytics analytics(long tenantId) {
        List<Distribution> regions = jdbc.query("""
                SELECT region AS label, COUNT(DISTINCT visitor_hash) AS count
                  FROM short_link_click_events e JOIN short_links l ON e.short_link_id=l.id
                 WHERE l.tenant_id=? GROUP BY region ORDER BY count DESC, region LIMIT 20
                """, (rs, row) -> new Distribution(rs.getString("label"), rs.getLong("count")), tenantId);
        List<Distribution> devices = jdbc.query("""
                SELECT device_type AS label, COUNT(DISTINCT visitor_hash) AS count
                  FROM short_link_click_events e JOIN short_links l ON e.short_link_id=l.id
                 WHERE l.tenant_id=? GROUP BY device_type ORDER BY count DESC, device_type LIMIT 20
                """, (rs, row) -> new Distribution(rs.getString("label"), rs.getLong("count")), tenantId);
        Long total = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT e.visitor_hash)
                  FROM short_link_click_events e JOIN short_links l ON e.short_link_id=l.id
                 WHERE l.tenant_id=?
                """, Long.class, tenantId);
        return new Analytics(total == null ? 0 : total, regions, devices);
    }

    @Transactional
    public void recordClick(long id, ClickCommand command) {
        ShortLinkRow row = requireLink(id);
        String visitor = sha256(row.shortCode() + ":" + text(command.visitorKey(), "anonymous"));
        jdbc.update("""
                INSERT INTO short_link_click_events(short_link_id, visitor_hash, region, device_type)
                VALUES (?, ?, ?, ?)
                """, id, visitor, text(command.region(), "UNKNOWN"), text(command.deviceType(), "UNKNOWN"));
        jdbc.update("UPDATE short_links SET click_count=(SELECT COUNT(*) FROM short_link_click_events WHERE short_link_id=?) WHERE id=?",
                id, id);
    }

    private TargetCheck reviewTarget(String rawUrl, List<String> redirectChain, List<String> resolvedIps) {
        URI uri = parsePublicUrl(rawUrl);
        DomainRecord domain = findDomain(uri.getHost().toLowerCase(Locale.ROOT), "TARGET")
                .orElseThrow(() -> new BusinessException("SHORTLINK_DOMAIN_NOT_APPROVED", "目标域名未备案或未批准"));
        List<String> checks = new ArrayList<>();
        checks.add("URL_VALID");
        if ("APPROVED".equals(domain.status())) {
            checks.add("DOMAIN_APPROVED");
        } else if ("BLACKLISTED".equals(domain.status())) {
            checks.add("DOMAIN_BLACKLISTED");
        } else {
            checks.add("DOMAIN_NOT_APPROVED");
        }
        if (domain.domainAgeDays() < 30) checks.add("DOMAIN_TOO_NEW");
        if (!"APPROVED".equals(domain.filingStatus())) checks.add("FILING_NOT_APPROVED");
        if (looksMalicious(uri.toString())) checks.add("MALICIOUS_SIGNAL");
        if (resolvedIps != null) {
            for (String ip : resolvedIps) {
                if (isPrivateAddress(ip)) checks.add("PRIVATE_NETWORK_RESOLUTION");
            }
        }
        if (redirectChain != null) {
            for (String next : redirectChain) {
                URI nextUri = parsePublicUrl(next);
                if (!Objects.equals(uri.getHost(), nextUri.getHost())) checks.add("REDIRECT_CHAIN_RECORDED");
            }
        }
        boolean blocked = checks.stream().anyMatch(item -> item.endsWith("NOT_APPROVED") || item.contains("BLACKLISTED")
                || item.contains("PRIVATE") || item.contains("MALICIOUS") || item.contains("TOO_NEW"));
        return new TargetCheck(uri.toString(), domain, blocked ? "BLOCKED" : "PASS", blocked ? "HIGH" : "LOW", checks);
    }

    private URI parsePublicUrl(String rawUrl) {
        String value = requireText(rawUrl, "SHORTLINK_URL_REQUIRED", 2000);
        try {
            URI uri = new URI(value).normalize();
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!List.of("http", "https").contains(scheme) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new BusinessException("SHORTLINK_URL_INVALID", "原始URL必须是合法的HTTP/HTTPS公网地址");
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (host.equals("localhost") || host.endsWith(".local") || host.endsWith(".internal") || isPrivateAddress(host)) {
                throw new BusinessException("SHORTLINK_URL_PRIVATE", "原始URL不能指向内网或本机地址");
            }
            return uri;
        } catch (URISyntaxException ex) {
            throw new BusinessException("SHORTLINK_URL_INVALID", "原始URL格式不合法");
        }
    }

    private DomainRecord requireApprovedDomain(String host, String kind) {
        return findDomain(host, kind)
                .filter(row -> "APPROVED".equals(row.status()))
                .orElseThrow(() -> new BusinessException("SHORTLINK_DOMAIN_NOT_APPROVED", "短链域名未备案或未批准"));
    }

    private java.util.Optional<DomainRecord> findDomain(String host, String kind) {
        List<DomainRecord> rows = jdbc.query("""
                SELECT domain_name, domain_kind, filing_status, domain_age_days, status, evidence_json
                  FROM short_link_domains WHERE domain_kind=?
                """, (rs, row) -> new DomainRecord(rs.getString("domain_name"), rs.getString("domain_kind"),
                rs.getString("filing_status"), rs.getInt("domain_age_days"), rs.getString("status"),
                rs.getString("evidence_json")), kind);
        return rows.stream()
                .filter(row -> matchesDomain(host, row.domainName()))
                .findFirst();
    }

    private static boolean matchesDomain(String host, String domain) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        String normalizedDomain = domain.toLowerCase(Locale.ROOT);
        return normalizedHost.equals(normalizedDomain) || normalizedHost.endsWith("." + normalizedDomain);
    }

    private static boolean looksMalicious(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("phishing") || lower.contains("malware") || lower.contains("token-steal");
    }

    private static boolean isPrivateAddress(String hostOrIp) {
        String value = hostOrIp == null ? "" : hostOrIp.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("10.") || value.startsWith("127.") || value.startsWith("169.254.") || value.startsWith("0.")) return true;
        if (value.startsWith("192.168.")) return true;
        if (value.matches("172\\.(1[6-9]|2[0-9]|3[0-1])\\..*")) return true;
        return value.equals("::1") || value.startsWith("fc") || value.startsWith("fd") || value.startsWith("fe80:");
    }

    private ShortLinkRow requireLink(long id) {
        return jdbc.queryForObject("SELECT * FROM short_links WHERE id=?", mapper(), id);
    }

    private void audit(long id, String action, String actor, String status, String evidence, String risk, String comment) {
        jdbc.update("""
                INSERT INTO short_link_audits(short_link_id, auto_check_result, risk_level, reviewer,
                    review_comment, reviewed_at, last_recheck_at, audit_action, actor, result_status, evidence_json)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?, ?, ?)
                """, id, evidence, risk, actor(actor), comment, action, actor(actor), status, evidence);
    }

    private RowMapper<ShortLinkRow> mapper() {
        return (rs, row) -> new ShortLinkRow(rs.getLong("id"), rs.getLong("tenant_id"),
                rs.getString("target_url"), rs.getString("custom_domain"), rs.getString("short_code"),
                rs.getString("short_url"), rs.getObject("valid_until", LocalDate.class), rs.getString("status"),
                rs.getLong("click_count"), rs.getInt("target_version"), rs.getString("immutable_target_sha256"),
                rs.getString("automated_result_json"), rs.getString("screenshot_evidence_ref"),
                rs.getString("domain_evidence_json"), rs.getString("risk_level"), rs.getString("review_opinion"),
                rs.getString("reviewed_by"), localDateTime(rs, "reviewed_at"), rs.getString("offline_reason"),
                localDateTime(rs, "offline_at"), localDateTime(rs, "last_recheck_at"));
    }

    private static LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private String nextCode() {
        String code;
        do {
            code = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        } while (Boolean.TRUE.equals(jdbc.queryForObject("SELECT COUNT(*)>0 FROM short_links WHERE short_code=?", Boolean.class, code)));
        return code;
    }

    private String json(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("cannot serialize short-link evidence", ex);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("sha256 unavailable", ex);
        }
    }

    private static String requireText(String value, String code, int max) {
        String text = text(value);
        if (text == null || text.length() > max) {
            throw new BusinessException(code, "短链字段不完整或超长");
        }
        return text;
    }

    private static String actor(String actor) {
        return text(actor, "system");
    }

    private static String text(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }

    private static String text(String value, String fallback) {
        String text = text(value);
        return text == null ? fallback : text;
    }

    private static String host(String value) {
        try {
            String candidate = value.contains("://") ? value : "https://" + value;
            URI uri = new URI(candidate);
            if (uri.getHost() == null) throw new URISyntaxException(value, "missing host");
            return uri.getHost().toLowerCase(Locale.ROOT);
        } catch (URISyntaxException ex) {
            throw new BusinessException("SHORTLINK_DOMAIN_INVALID", "短链域名格式不合法");
        }
    }

    private static long key(GeneratedKeyHolder keys) {
        Number id = keys.getKey();
        if (id == null) throw new IllegalStateException("short-link id was not returned");
        return id.longValue();
    }

    private static String safeMessage(String status) {
        return switch (status) {
            case "REJECTED" -> "短链未通过审核，已停止跳转";
            case "EXPIRED" -> "短链已过期，已停止跳转";
            case "OFFLINE", "TAKEN_DOWN" -> "短链目标巡检异常，已下线";
            default -> "短链待审核，暂不跳转";
        };
    }

    private record DomainRecord(String domainName, String domainKind, String filingStatus,
                                int domainAgeDays, String status, String evidenceJson) { }

    private record TargetCheck(String normalizedUrl, DomainRecord targetDomain, String verdict,
                               String riskLevel, List<String> checks) { }

    public record CreateCommand(Long tenantId, String originalUrl, String customDomain, LocalDate validUntil,
                                List<String> redirectChain, List<String> resolvedIps) {
        CreateCommand checked() {
            if (tenantId == null || tenantId <= 0) {
                throw new BusinessException("SHORTLINK_TENANT_REQUIRED", "机构ID不能为空");
            }
            return this;
        }
    }

    public record ReviewCommand(String opinion) { }

    public record InspectCommand(String observedTargetUrl, List<String> resolvedIps) { }

    public record ClickCommand(String visitorKey, String region, String deviceType) { }

    public record RedirectDecision(boolean redirect, String targetUrl, String state, String message) {
        static RedirectDecision safe(String state, String message) {
            return new RedirectDecision(false, null, state, message);
        }
    }

    public record Analytics(long uniqueClicks, List<Distribution> regions, List<Distribution> devices) { }

    public record Distribution(String label, long count) { }

    public record ShortLinkRow(long id, long tenantId, String targetUrl, String customDomain, String shortCode,
                               String shortUrl, LocalDate validUntil, String status, long clickCount,
                               int targetVersion, String immutableTargetSha256, String automatedResultJson,
                               String screenshotEvidenceRef, String domainEvidenceJson, String riskLevel,
                               String reviewOpinion, String reviewedBy, LocalDateTime reviewedAt,
                               String offlineReason, LocalDateTime offlineAt, LocalDateTime lastRecheckAt) { }
}
