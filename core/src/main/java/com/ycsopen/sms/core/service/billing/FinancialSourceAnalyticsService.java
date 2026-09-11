package com.ycsopen.sms.core.service.billing;

import com.ycsopen.sms.core.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Phase 39: live financial analytics reconciled to immutable message and pricing sources. */
@Service
public class FinancialSourceAnalyticsService {
    private static final String FORMULA_VERSION = "FINANCIAL_SOURCE_V1";
    private static final String FORMULA = "providerCostMil=sum(message_tasks.cost*1000), "
            + "revenueMil=billableFinalCount*tenant_price_books.unit_price_mil, "
            + "profitMil=revenueMil-providerCostMil";

    private final JdbcTemplate jdbc;

    public FinancialSourceAnalyticsService(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Transactional(readOnly = true)
    public List<FinancialSummaryRow> summaries(FinancialAnalyticsFilter filter) {
        Period period = period(filter);
        List<Object> params = new ArrayList<>();
        params.add(period.startInclusive());
        params.add(period.endExclusive());
        StringBuilder sql = new StringBuilder("""
                       SELECT t.tenant_id, t.channel_id,
                       COALESCE(c.price_book_version, 'NO_ACTIVE_CONTRACT') AS price_book_version,
                       COALESCE(pb.unit_price_mil, 0) AS unit_price_mil,
                       COUNT(*) AS source_count,
                       SUM(CASE WHEN final_status IN ('SENT','DELIVERED') THEN 1 ELSE 0 END) AS billable_count,
                       COALESCE(SUM(ROUND(t.cost * 1000, 0)), 0) AS provider_cost_mil,
                       COALESCE(SUM(CASE WHEN final_status IN ('SENT','DELIVERED') THEN COALESCE(pb.unit_price_mil, 0) ELSE 0 END), 0) AS revenue_mil,
                       MAX(GREATEST(t.updated_at, COALESCE(latest_report_time, t.updated_at))) AS freshness_at
                  FROM (
                        SELECT mt.*,
                               COALESCE((
                                   SELECT dr.report_status
                                     FROM delivery_reports dr
                                    WHERE dr.message_id=mt.message_id
                                    ORDER BY dr.report_time DESC, dr.id DESC
                                    LIMIT 1
                               ), mt.send_status) AS final_status,
                               (
                                   SELECT MAX(dr.report_time)
                                     FROM delivery_reports dr
                                    WHERE dr.message_id=mt.message_id
                               ) AS latest_report_time
                          FROM message_tasks mt
                         WHERE mt.created_at>=? AND mt.created_at<?
                       ) t
                  LEFT JOIN tenant_contracts c ON c.tenant_id=t.tenant_id AND c.contract_status='ACTIVE'
                  LEFT JOIN tenant_price_books pb ON pb.price_book_version=c.price_book_version AND pb.status='ACTIVE'
                 WHERE 1=1
                """);
        appendFilters(sql, params, filter);
        sql.append("""
                 GROUP BY t.tenant_id, t.channel_id, COALESCE(c.price_book_version, 'NO_ACTIVE_CONTRACT'), COALESCE(pb.unit_price_mil, 0)
                 ORDER BY t.tenant_id, t.channel_id
                 LIMIT 500
                """);
        return jdbc.query(sql.toString(), (rs, row) -> summary(rs, period), params.toArray());
    }

    @Transactional(readOnly = true)
    public List<FinancialSourceRow> drilldown(FinancialAnalyticsFilter filter) {
        Period period = period(filter);
        List<Object> params = new ArrayList<>();
        params.add(period.startInclusive());
        params.add(period.endExclusive());
        StringBuilder sql = new StringBuilder("""
                SELECT t.id AS task_id, t.message_id, t.tenant_id, t.channel_id, t.send_status,
                       COALESCE((
                           SELECT dr.report_status
                             FROM delivery_reports dr
                            WHERE dr.message_id=t.message_id
                            ORDER BY dr.report_time DESC, dr.id DESC
                            LIMIT 1
                       ), t.send_status) AS final_status,
                       CAST(ROUND(t.cost * 1000, 0) AS BIGINT) AS provider_cost_mil,
                       COALESCE(c.price_book_version, 'NO_ACTIVE_CONTRACT') AS price_book_version,
                       COALESCE(pb.unit_price_mil, 0) AS unit_price_mil,
                       CASE WHEN COALESCE((
                           SELECT dr.report_status
                             FROM delivery_reports dr
                            WHERE dr.message_id=t.message_id
                            ORDER BY dr.report_time DESC, dr.id DESC
                            LIMIT 1
                       ), t.send_status) IN ('SENT','DELIVERED') THEN COALESCE(pb.unit_price_mil, 0) ELSE 0 END AS revenue_mil,
                       GREATEST(t.updated_at, COALESCE((
                           SELECT MAX(dr.report_time)
                             FROM delivery_reports dr
                            WHERE dr.message_id=t.message_id
                       ), t.updated_at)) AS freshness_at
                  FROM message_tasks t
                  LEFT JOIN tenant_contracts c ON c.tenant_id=t.tenant_id AND c.contract_status='ACTIVE'
                  LEFT JOIN tenant_price_books pb ON pb.price_book_version=c.price_book_version AND pb.status='ACTIVE'
                 WHERE t.created_at>=? AND t.created_at<?
                """);
        appendFilters(sql, params, filter);
        sql.append(" ORDER BY t.created_at DESC, t.id DESC LIMIT 200");
        return jdbc.query(sql.toString(), (rs, row) -> source(rs), params.toArray());
    }

    private static void appendFilters(StringBuilder sql, List<Object> params, FinancialAnalyticsFilter filter) {
        if (filter != null && filter.tenantId() != null) {
            sql.append(" AND t.tenant_id=?");
            params.add(filter.tenantId());
        }
        if (filter != null && filter.channelId() != null) {
            sql.append(" AND t.channel_id=?");
            params.add(filter.channelId());
        }
    }

    private static Period period(FinancialAnalyticsFilter filter) {
        LocalDate today = LocalDate.now();
        LocalDate start = filter == null || filter.startDate() == null ? today.withDayOfMonth(1) : filter.startDate();
        LocalDate end = filter == null || filter.endDate() == null ? today : filter.endDate();
        if (end.isBefore(start)) {
            throw new BusinessException("FINANCIAL_ANALYTICS_PERIOD_INVALID", "财务统计周期不合法");
        }
        return new Period(start, end.plusDays(1));
    }

    private static FinancialSummaryRow summary(ResultSet rs, Period period) throws SQLException {
        long cost = rs.getLong("provider_cost_mil");
        long revenue = rs.getLong("revenue_mil");
        return new FinancialSummaryRow(rs.getLong("tenant_id"), nullableLong(rs, "channel_id"),
                period.startInclusive().toLocalDate(), period.endExclusive().minusDays(1).toLocalDate(),
                rs.getLong("source_count"), rs.getLong("billable_count"), cost, revenue, revenue - cost,
                rs.getString("price_book_version"), rs.getLong("unit_price_mil"), FORMULA_VERSION, FORMULA,
                timestamp(rs.getTimestamp("freshness_at")));
    }

    private static FinancialSourceRow source(ResultSet rs) throws SQLException {
        long cost = rs.getLong("provider_cost_mil");
        long revenue = rs.getLong("revenue_mil");
        return new FinancialSourceRow(rs.getLong("task_id"), rs.getString("message_id"), rs.getLong("tenant_id"),
                nullableLong(rs, "channel_id"), rs.getString("send_status"), rs.getString("final_status"),
                cost, revenue, revenue - cost, rs.getString("price_book_version"), rs.getLong("unit_price_mil"),
                FORMULA_VERSION, FORMULA, timestamp(rs.getTimestamp("freshness_at")));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime timestamp(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    public record FinancialAnalyticsFilter(LocalDate startDate, LocalDate endDate, Long tenantId, Long channelId) { }

    public record FinancialSummaryRow(long tenantId, Long channelId, LocalDate periodStart, LocalDate periodEnd,
                                      long sourceCount, long billableCount, long providerCostMil, long revenueMil,
                                      long profitMil, String priceBookVersion, long unitPriceMil,
                                      String formulaVersion, String formula, LocalDateTime freshnessAt) { }

    public record FinancialSourceRow(long taskId, String messageId, long tenantId, Long channelId,
                                     String sendStatus, String finalStatus, long providerCostMil, long revenueMil,
                                     long profitMil, String priceBookVersion, long unitPriceMil,
                                     String formulaVersion, String formula, LocalDateTime freshnessAt) { }

    private record Period(LocalDateTime startInclusive, LocalDateTime endExclusive) {
        private Period(LocalDate startInclusive, LocalDate endExclusive) {
            this(startInclusive.atStartOfDay(), endExclusive.atStartOfDay());
        }
    }
}
