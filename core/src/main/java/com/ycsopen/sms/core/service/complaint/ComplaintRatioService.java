package com.ycsopen.sms.core.service.complaint;

import com.ycsopen.sms.core.domain.entity.Channel;
import com.ycsopen.sms.core.domain.entity.ComplaintRatioStats;
import com.ycsopen.sms.core.repository.ChannelRepository;
import com.ycsopen.sms.core.repository.ComplaintRatioStatsRepository;
import com.ycsopen.sms.core.repository.ComplaintRepository;
import com.ycsopen.sms.core.repository.MessageTaskRepository;
import com.ycsopen.sms.core.repository.TenantRepository;
import com.ycsopen.sms.core.repository.TenantRepository.IdProjection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * F-11.9 [新增] 仪表盘"通道 / 机构月度投诉占比看板"的计算引擎。
 * <p>指标定义（PRD 7.2 节）：投诉占比 = 当月投诉工单数 ÷ 当月发送量 × 100%，分别按通道、机构维度独立计算。
 * 超过阈值（默认千分之三 0.3%，可配置——见 application.yml
 * {@code ycsopen.routing.complaint-ratio-threshold} 与 F-11.10）的记录标记 overThreshold=true，
 * 前端据此标红置顶（见 web/src/pages/admin/dashboard 的看板组件）。</p>
 * <p>PRD 检视 Finding #3 在此留痕：分子完全依赖 {@code complaints} 表的人工登记时效性，
 * 若运营录入滞后，本服务算出的占比也会滞后失真——这是产品/运营流程问题，不是本服务能单独修复的，
 * 已记录在 core/docs/ROADMAP.md 的已知限制里。</p>
 */
@Service
public class ComplaintRatioService {

    private final TenantRepository tenantRepository;
    private final ChannelRepository channelRepository;
    private final MessageTaskRepository messageTaskRepository;
    private final ComplaintRepository complaintRepository;
    private final ComplaintRatioStatsRepository complaintRatioStatsRepository;
    private final BigDecimal threshold;

    private static final String THRESHOLD_CONFIG_VERSION = "default-v1";

    public ComplaintRatioService(TenantRepository tenantRepository,
                                  ChannelRepository channelRepository,
                                  MessageTaskRepository messageTaskRepository,
                                  ComplaintRepository complaintRepository,
                                  ComplaintRatioStatsRepository complaintRatioStatsRepository,
                                  @Value("${ycsopen.routing.complaint-ratio-threshold:0.003}") double threshold) {
        this.tenantRepository = tenantRepository;
        this.channelRepository = channelRepository;
        this.messageTaskRepository = messageTaskRepository;
        this.complaintRepository = complaintRepository;
        this.complaintRatioStatsRepository = complaintRatioStatsRepository;
        this.threshold = BigDecimal.valueOf(threshold);
    }

    /** PRD 7.2 节："当月累计数据每小时刷新一次"。 */
    @Scheduled(cron = "0 0 * * * *")
    public void scheduledRecalculateCurrentMonth() {
        recalculate(YearMonth.now());
    }

    @Transactional
    public void recalculate(YearMonth month) {
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.plusMonths(1).atDay(1).atStartOfDay();
        String statMonth = month.format(DateTimeFormatter.ofPattern("yyyy-MM"));

        for (IdProjection tenant : tenantRepository.findAllIds()) {
            long sendCount = messageTaskRepository.countByTenantIdAndCreatedAtBetween(tenant.getId(), start, end);
            long complaintCount = complaintRepository.countByTenantIdAndCreatedAtBetween(tenant.getId(), start, end);
            upsert(statMonth, ComplaintRatioStats.DimensionType.TENANT, tenant.getId(), sendCount, complaintCount);
        }

        for (Channel channel : channelRepository.findAll()) {
            long sendCount = messageTaskRepository.countByChannelIdAndCreatedAtBetween(channel.getId(), start, end);
            long complaintCount = complaintRepository.countByChannelIdAndCreatedAtBetween(channel.getId(), start, end);
            upsert(statMonth, ComplaintRatioStats.DimensionType.CHANNEL, channel.getId(), sendCount, complaintCount);
        }
    }

    private void upsert(String statMonth, ComplaintRatioStats.DimensionType type, Long dimensionId,
                         long sendCount, long complaintCount) {
        BigDecimal ratio = sendCount == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(complaintCount).divide(BigDecimal.valueOf(sendCount), new MathContext(8));

        ComplaintRatioStats stats = findExisting(statMonth, type, dimensionId).orElseGet(ComplaintRatioStats::new);
        stats.setStatMonth(statMonth);
        stats.setDimensionType(type);
        stats.setDimensionId(dimensionId);
        stats.setSendCount(sendCount);
        stats.setComplaintCount(complaintCount);
        stats.setRatio(ratio);
        stats.setOverThreshold(ratio.compareTo(threshold) >= 0);
        stats.setCalculatedAt(LocalDateTime.now());
        complaintRatioStatsRepository.save(stats);
    }

    private Optional<ComplaintRatioStats> findExisting(String statMonth, ComplaintRatioStats.DimensionType type, Long dimensionId) {
        return complaintRatioStatsRepository.findByStatMonthAndDimensionTypeAndDimensionId(statMonth, type, dimensionId);
    }

    /** 供 Dashboard Controller 调用：F-11.9 排行榜，超阈值优先，其余按占比降序。 */
    public List<ComplaintRatioStats> getRanking(YearMonth month, ComplaintRatioStats.DimensionType type) {
        String statMonth = month.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        return complaintRatioStatsRepository.findByStatMonthAndDimensionTypeOrderByRatioDesc(statMonth, type);
    }
}
