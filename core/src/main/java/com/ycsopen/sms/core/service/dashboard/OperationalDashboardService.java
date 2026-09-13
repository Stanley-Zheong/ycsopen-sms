package com.ycsopen.sms.core.service.dashboard;

import com.ycsopen.sms.core.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class OperationalDashboardService {
    private final JdbcTemplate jdbc;

    public OperationalDashboardService(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Transactional(readOnly = true)
    public PlatformDashboard platformDashboard() {
        MetricSource source = metricSource("CHANNEL_DELIVERY", "PLATFORM");
        int send = intValue("""
                SELECT COALESCE(SUM(send_count),0) FROM statistics_aggregates
                 WHERE metric_code='CHANNEL_DELIVERY' AND bucket_date=CURRENT_DATE
                """);
        int success = intValue("""
                SELECT COALESCE(SUM(success_count),0) FROM statistics_aggregates
                 WHERE metric_code='CHANNEL_DELIVERY' AND bucket_date=CURRENT_DATE
                """);
        BigDecimal revenue = decimalValue("""
                SELECT COALESCE(SUM(fee_amount),0) FROM statistics_aggregates
                 WHERE metric_code='CHANNEL_DELIVERY' AND bucket_date=CURRENT_DATE
                """);
        RealtimeCards realtime = new RealtimeCards(
                intValue("SELECT COUNT(*) FROM users"),
                send,
                rate(success, send),
                intValue("SELECT COUNT(*) FROM tenants WHERE lifecycle_status IN ('TRIAL','SIGNED')"),
                comparison(send, "CHANNEL_DELIVERY"));
        KpiCards kpi = new KpiCards(send, realtime.activeTenants(), rate(success, send), revenue, source.formula());
        return new PlatformDashboard(
                realtime,
                kpi,
                hourlyTrend(null),
                tenantRank(),
                channelHealth(),
                financeWarning(),
                source);
    }

    @Transactional(readOnly = true)
    public TenantOverview tenantOverview(Actor actor, Long requestedTenantId) {
        Actor checked = requireActor(actor);
        long tenantId = checked.tenantId() == null ? Objects.requireNonNullElse(requestedTenantId, 0L) : checked.tenantId();
        if (tenantId <= 0) {
            throw new BusinessException("OPERATIONAL_DASHBOARD_TENANT_REQUIRED", "租户范围不能为空");
        }
        if (checked.tenantId() != null && requestedTenantId != null && !Objects.equals(checked.tenantId(), requestedTenantId)) {
            throw new BusinessException("OPERATIONAL_DASHBOARD_TENANT_FORBIDDEN", "租户范围无权访问");
        }
        int send = intValue("SELECT COALESCE(SUM(send_count),0) FROM statistics_aggregates WHERE metric_code='TENANT_BEHAVIOR' AND tenant_id=? AND bucket_date=CURRENT_DATE", tenantId);
        int success = intValue("SELECT COALESCE(SUM(success_count),0) FROM statistics_aggregates WHERE metric_code='TENANT_BEHAVIOR' AND tenant_id=? AND bucket_date=CURRENT_DATE", tenantId);
        return new TenantOverview(
                tenantId,
                longValue("SELECT COALESCE(balance_mil,0) FROM prepaid_accounts WHERE tenant_id=?", tenantId),
                stringValue("SELECT COALESCE(status,'NONE') FROM trial_accounts WHERE tenant_id=?", "NONE", tenantId),
                stringValue("SELECT COALESCE(contract_status,'NONE') FROM tenant_contracts WHERE tenant_id=?", "NONE", tenantId),
                send,
                rate(success, send),
                serviceStatus(tenantId),
                metricSource("TENANT_BEHAVIOR", "TENANT"));
    }

    @Transactional(readOnly = true)
    public ResourceStatistics resourceStatistics(Actor actor, Long tenantId) {
        Actor checked = requireActor(actor);
        Long scopedTenant = checked.tenantId() == null ? tenantId : checked.tenantId();
        if (checked.tenantId() != null && tenantId != null && !Objects.equals(checked.tenantId(), tenantId)) {
            throw new BusinessException("OPERATIONAL_DASHBOARD_TENANT_FORBIDDEN", "租户范围无权访问");
        }
        List<ResourceRow> resources = scopedTenant == null
                ? jdbc.query(resourceSql(""), (rs, row) -> resource(rs))
                : jdbc.query(resourceSql(" AND tenant_id=?"), (rs, row) -> resource(rs), scopedTenant);
        List<ChannelComparisonRow> channels = scopedTenant == null
                ? jdbc.query(channelSql(""), (rs, row) -> channel(rs))
                : jdbc.query(channelSql(" AND tenant_id=?"), (rs, row) -> channel(rs), scopedTenant);
        return new ResourceStatistics(resources, channels,
                List.of("tenant_id", "signature_id", "template_id", "submit_count", "success_count", "rejected_count", "freshness_at"),
                metricSource("RESOURCE_USAGE", scopedTenant == null ? "PLATFORM" : "TENANT"),
                resources.isEmpty() && channels.isEmpty(),
                "NONE");
    }

    @Transactional(readOnly = true)
    public ApiStatus apiStatus() {
        LocalDateTime freshness = latestFreshness();
        List<HealthRow> rows = List.of(
                new HealthRow("DATABASE", "NORMAL", "statistics_aggregates", freshness, "dashboard query source", "health|database|statistics_aggregates"),
                new HealthRow("PROVIDER", providerStatus(), "statistics_aggregates", freshness, "delivery status freshness", "health|provider|statistics_aggregates"),
                new HealthRow("CHANNEL", channelHealth().abnormal() > 0 ? "DEGRADED" : "NORMAL", "channels", freshness, "channel status counts", "health|channel|channels"));
        return new ApiStatus(rows, new MetricSource("operational_source_tables",
                "component status derives from live table counts and aggregate freshness", freshness,
                "PLATFORM", "v1"));
    }

    @Transactional(readOnly = true)
    public DashboardConfiguration configuration(String role) {
        String checkedRole = role(role);
        List<DashboardConfiguration> rows = jdbc.query("""
                SELECT role_code, global_cards, tenant_cards, refresh_mode, polling_seconds,
                       complaint_threshold, updated_by, updated_at
                  FROM operational_dashboard_configs
                 WHERE role_code=?
                """, (rs, row) -> configuration(rs), checkedRole);
        if (!rows.isEmpty()) {
            return rows.getFirst();
        }
        boolean tenant = checkedRole.startsWith("TENANT_");
        return new DashboardConfiguration(checkedRole, !tenant, true, "MANUAL", 300, "0.0030",
                "system", null);
    }

    @Transactional
    public DashboardConfiguration saveConfiguration(DashboardConfigurationCommand command, String actor) {
        DashboardConfigurationCommand checked = command.checked();
        boolean tenantRole = checked.role().startsWith("TENANT_");
        boolean globalCards = tenantRole ? false : checked.globalCards();
        boolean tenantCards = checked.tenantCards();
        int updated = jdbc.update("""
                UPDATE operational_dashboard_configs
                   SET global_cards=?, tenant_cards=?, refresh_mode=?, polling_seconds=?,
                       complaint_threshold=?, updated_by=?, updated_at=CURRENT_TIMESTAMP
                 WHERE role_code=?
                """, globalCards, tenantCards, checked.refreshMode(), checked.pollingSeconds(),
                checked.complaintThreshold(), actor(actor), checked.role());
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO operational_dashboard_configs(role_code, global_cards, tenant_cards, refresh_mode,
                        polling_seconds, complaint_threshold, updated_by)
                    VALUES (?,?,?,?,?,?,?)
                    """, checked.role(), globalCards, tenantCards, checked.refreshMode(), checked.pollingSeconds(),
                    checked.complaintThreshold(), actor(actor));
        }
        return configuration(checked.role());
    }

    private List<HourlyTrendRow> hourlyTrend(Long tenantId) {
        return tenantId == null
                ? jdbc.query("""
                    SELECT bucket_start, SUM(send_count) send_count, SUM(success_count) success_count
                      FROM statistics_aggregates
                     WHERE metric_code='CHANNEL_DELIVERY' AND bucket_date=CURRENT_DATE
                     GROUP BY bucket_start
                     ORDER BY bucket_start
                    """, (rs, row) -> hourly(rs))
                : jdbc.query("""
                    SELECT bucket_start, SUM(send_count) send_count, SUM(success_count) success_count
                      FROM statistics_aggregates
                     WHERE metric_code='TENANT_BEHAVIOR' AND bucket_date=CURRENT_DATE AND tenant_id=?
                     GROUP BY bucket_start
                     ORDER BY bucket_start
                    """, (rs, row) -> hourly(rs), tenantId);
    }

    private List<TenantRankRow> tenantRank() {
        return jdbc.query("""
                SELECT tenant_id, SUM(send_count) send_count, SUM(success_count) success_count
                  FROM statistics_aggregates
                 WHERE metric_code='TENANT_BEHAVIOR' AND bucket_date=CURRENT_DATE
                 GROUP BY tenant_id
                 ORDER BY send_count DESC, tenant_id ASC
                 LIMIT 5
                """, (rs, row) -> new TenantRankRow(rs.getLong("tenant_id"), rs.getInt("send_count"),
                rs.getInt("success_count"), rate(rs.getInt("success_count"), rs.getInt("send_count"))));
    }

    private ChannelHealth channelHealth() {
        return new ChannelHealth(
                intValue("SELECT COUNT(*) FROM channels WHERE status='NORMAL'"),
                intValue("SELECT COUNT(*) FROM channels WHERE status='MAINTENANCE'"),
                intValue("SELECT COUNT(*) FROM channels WHERE status NOT IN ('NORMAL','MAINTENANCE')"));
    }

    private FinanceWarning financeWarning() {
        return new FinanceWarning(intValue("SELECT COUNT(*) FROM fee_warning_episodes WHERE status='ACTIVE'"),
                timestampValue("SELECT MAX(updated_at) FROM fee_warning_episodes WHERE status='ACTIVE'"));
    }

    private MetricSource metricSource(String metricCode, String permissionScope) {
        List<MetricSource> rows = jdbc.query("""
                SELECT formula, formula_version, permission_scope,
                       COALESCE((SELECT MAX(freshness_at) FROM statistics_aggregates WHERE metric_code=?), updated_at) AS freshness_at
                  FROM statistics_metric_registry
                 WHERE metric_code=? AND status='ACTIVE'
                """, (rs, row) -> new MetricSource("statistics_aggregates", rs.getString("formula"),
                timestamp(rs.getTimestamp("freshness_at")),
                permissionScope == null ? rs.getString("permission_scope") : permissionScope,
                rs.getString("formula_version")), metricCode, metricCode);
        return rows.isEmpty() ? new MetricSource("statistics_aggregates", metricCode, latestFreshness(),
                permissionScope, "v1") : rows.getFirst();
    }

    private String serviceStatus(long tenantId) {
        int abnormal = intValue("SELECT COUNT(*) FROM statistics_aggregates WHERE tenant_id=? AND quality_state<>'FRESH'", tenantId);
        return abnormal == 0 ? "NORMAL" : "DEGRADED";
    }

    private String providerStatus() {
        return intValue("SELECT COUNT(*) FROM statistics_aggregates WHERE quality_state<>'FRESH'") == 0 ? "NORMAL" : "DEGRADED";
    }

    private String resourceSql(String tenantClause) {
        return """
                SELECT tenant_id, signature_id, template_id, SUM(submit_count) submit_count,
                       SUM(success_count) success_count, SUM(rejected_count) rejected_count,
                       MAX(freshness_at) freshness_at
                  FROM statistics_aggregates
                 WHERE metric_code='RESOURCE_USAGE'%s
                 GROUP BY tenant_id, signature_id, template_id
                 ORDER BY tenant_id, signature_id, template_id
                """.formatted(tenantClause);
    }

    private String channelSql(String tenantClause) {
        return """
                SELECT tenant_id, channel_id, SUM(send_count) send_count, SUM(success_count) success_count,
                       SUM(failure_count) failure_count, MAX(freshness_at) freshness_at
                  FROM statistics_aggregates
                 WHERE metric_code='CHANNEL_DELIVERY'%s
                 GROUP BY tenant_id, channel_id
                 ORDER BY tenant_id, channel_id
                """.formatted(tenantClause);
    }

    private ResourceRow resource(ResultSet rs) throws SQLException {
        return new ResourceRow(rs.getLong("tenant_id"), nullableLong(rs, "signature_id"),
                nullableLong(rs, "template_id"), rs.getInt("submit_count"), rs.getInt("success_count"),
                rs.getInt("rejected_count"), timestamp(rs.getTimestamp("freshness_at")));
    }

    private ChannelComparisonRow channel(ResultSet rs) throws SQLException {
        return new ChannelComparisonRow(rs.getLong("tenant_id"), nullableLong(rs, "channel_id"),
                rs.getInt("send_count"), rs.getInt("success_count"), rs.getInt("failure_count"),
                rate(rs.getInt("success_count"), rs.getInt("send_count")), timestamp(rs.getTimestamp("freshness_at")));
    }

    private HourlyTrendRow hourly(ResultSet rs) throws SQLException {
        return new HourlyTrendRow(timestamp(rs.getTimestamp("bucket_start")), rs.getInt("send_count"),
                rs.getInt("success_count"), rate(rs.getInt("success_count"), rs.getInt("send_count")));
    }

    private DashboardConfiguration configuration(ResultSet rs) throws SQLException {
        return new DashboardConfiguration(rs.getString("role_code"), rs.getBoolean("global_cards"),
                rs.getBoolean("tenant_cards"), rs.getString("refresh_mode"), rs.getInt("polling_seconds"),
                rs.getString("complaint_threshold"), rs.getString("updated_by"), timestamp(rs.getTimestamp("updated_at")));
    }

    private int intValue(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private long longValue(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private BigDecimal decimalValue(String sql, Object... args) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class, args);
        return value == null ? BigDecimal.ZERO : value;
    }

    private String stringValue(String sql, String fallback, Object... args) {
        List<String> values = jdbc.query(sql, (rs, row) -> rs.getString(1), args);
        return values.isEmpty() || values.getFirst() == null ? fallback : values.getFirst();
    }

    private LocalDateTime timestampValue(String sql, Object... args) {
        Timestamp value = jdbc.queryForObject(sql, Timestamp.class, args);
        return timestamp(value);
    }

    private LocalDateTime latestFreshness() {
        return timestampValue("SELECT MAX(freshness_at) FROM statistics_aggregates");
    }

    private static BigDecimal rate(int numerator, int denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(4);
        }
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    private static int comparison(int value, String metricCode) {
        return value;
    }

    private static Actor requireActor(Actor actor) {
        if (actor == null || text(actor.name()) == null) {
            throw new BusinessException("OPERATIONAL_DASHBOARD_ACTOR_REQUIRED", "操作人不能为空");
        }
        return actor;
    }

    private static String role(String role) {
        String checked = text(role) == null ? "ADMIN" : role.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ADMIN", "OPERATOR", "FINANCE", "TENANT_ADMIN", "TENANT_DEV", "TENANT_USER").contains(checked)) {
            throw new BusinessException("OPERATIONAL_DASHBOARD_ROLE_UNSUPPORTED", "仪表盘角色不支持");
        }
        return checked;
    }

    private static String actor(String actor) {
        return text(actor) == null ? "system" : actor.trim();
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime timestamp(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record Actor(String name, Long tenantId) {
        public static Actor platform(String name) {
            return new Actor(name, null);
        }

        public static Actor tenant(String name, long tenantId) {
            return new Actor(name, tenantId);
        }
    }

    public record PlatformDashboard(RealtimeCards realtime, KpiCards kpi, List<HourlyTrendRow> hourlyTrend,
                                    List<TenantRankRow> tenantRank, ChannelHealth channelHealth,
                                    FinanceWarning financeWarning, MetricSource source) { }

    public record RealtimeCards(int totalUsers, int todayMessages, BigDecimal successRate,
                                int activeTenants, int comparisonMessages) { }

    public record KpiCards(int todaySend, int activeTenants, BigDecimal successRate,
                           BigDecimal todayRevenue, String formula) { }

    public record HourlyTrendRow(LocalDateTime bucketStart, int sendCount, int successCount,
                                 BigDecimal successRate) { }

    public record TenantRankRow(long tenantId, int sendCount, int successCount, BigDecimal successRate) { }

    public record ChannelHealth(int normal, int maintenance, int abnormal) { }

    public record FinanceWarning(int warningCount, LocalDateTime freshnessAt) { }

    public record TenantOverview(long tenantId, long balanceMil, String trialStatus, String contractStatus,
                                 int todayMessages, BigDecimal successRate, String serviceStatus,
                                 MetricSource source) { }

    public record ResourceStatistics(List<ResourceRow> resources, List<ChannelComparisonRow> channelComparisons,
                                     List<String> accessibleColumns, MetricSource source,
                                     boolean empty, String errorState) { }

    public record ResourceRow(long tenantId, Long signatureId, Long templateId, int submitCount,
                              int successCount, int rejectedCount, LocalDateTime freshnessAt) { }

    public record ChannelComparisonRow(long tenantId, Long channelId, int sendCount, int successCount,
                                       int failureCount, BigDecimal successRate, LocalDateTime freshnessAt) { }

    public record ApiStatus(List<HealthRow> rows, MetricSource source) { }

    public record HealthRow(String component, String status, String source, LocalDateTime freshnessAt,
                            String impact, String drilldownKey) { }

    public record MetricSource(String registry, String formula, LocalDateTime freshnessAt,
                               String permissionScope, String formulaVersion) { }

    public record DashboardConfiguration(String role, boolean globalCards, boolean tenantCards,
                                         String refreshMode, int pollingSeconds, String complaintThreshold,
                                         String updatedBy, LocalDateTime updatedAt) { }

    public record DashboardConfigurationCommand(String role, boolean globalCards, boolean tenantCards,
                                                String refreshMode, int pollingSeconds,
                                                String complaintThreshold) {
        DashboardConfigurationCommand checked() {
            String checkedRole = OperationalDashboardService.role(role);
            String checkedRefreshMode = text(refreshMode) == null ? "MANUAL" : refreshMode.trim().toUpperCase(Locale.ROOT);
            if (!List.of("MANUAL", "POLLING").contains(checkedRefreshMode)) {
                throw new BusinessException("OPERATIONAL_DASHBOARD_REFRESH_UNSUPPORTED", "刷新模式不支持");
            }
            int checkedPolling = Math.max(30, pollingSeconds);
            String threshold = text(complaintThreshold) == null ? "0.0030" : complaintThreshold.trim();
            return new DashboardConfigurationCommand(checkedRole, globalCards, tenantCards, checkedRefreshMode,
                    checkedPolling, threshold);
        }
    }
}
