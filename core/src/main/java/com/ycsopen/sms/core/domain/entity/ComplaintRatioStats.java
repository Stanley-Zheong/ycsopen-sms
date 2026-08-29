package com.ycsopen.sms.core.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * F-11.9 [新增] 投诉占比统计视图，驱动"通道/机构月度投诉占比看板"。
 * ratio = complaintCount / sendCount；overThreshold 由 ComplaintRatioService 按可配置阈值
 * （默认千分之三，见 application.yml ycsopen.routing.complaint-ratio-threshold）计算写入。
 */
@Entity
@Table(name = "complaint_ratio_stats")
@Getter
@Setter
public class ComplaintRatioStats {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stat_month", nullable = false)
    private String statMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "dimension_type", nullable = false)
    private DimensionType dimensionType;

    @Column(name = "dimension_id", nullable = false)
    private Long dimensionId;

    @Column(name = "send_count")
    private Long sendCount = 0L;

    @Column(name = "complaint_count")
    private Long complaintCount = 0L;

    private BigDecimal ratio = BigDecimal.ZERO;

    @Column(name = "over_threshold")
    private Boolean overThreshold = false;

    @Column(name = "calculated_at")
    private LocalDateTime calculatedAt = LocalDateTime.now();

    public enum DimensionType { CHANNEL, TENANT }
}
