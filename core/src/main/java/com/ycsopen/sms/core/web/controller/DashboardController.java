package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.domain.entity.ComplaintRatioStats;
import com.ycsopen.sms.core.service.complaint.ComplaintRatioService;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import com.ycsopen.sms.core.web.dto.ComplaintRatioItemResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

/** F-11.9 仪表盘"通道/机构月度投诉占比看板"数据接口。 */
@RestController
@RequestMapping("/api/v1/console/dashboard")
public class DashboardController {

    private final ComplaintRatioService complaintRatioService;

    public DashboardController(ComplaintRatioService complaintRatioService) {
        this.complaintRatioService = complaintRatioService;
    }

    @GetMapping("/complaint-ratio/channel")
    public ApiResponse<List<ComplaintRatioItemResponse>> channelRanking(
            @RequestParam(required = false) String month) {
        YearMonth ym = month == null ? YearMonth.now() : YearMonth.parse(month);
        List<ComplaintRatioStats> stats = complaintRatioService.getRanking(ym, ComplaintRatioStats.DimensionType.CHANNEL);
        return ApiResponse.ok(stats.stream().map(this::toResponse).toList());
    }

    @GetMapping("/complaint-ratio/tenant")
    public ApiResponse<List<ComplaintRatioItemResponse>> tenantRanking(
            @RequestParam(required = false) String month) {
        YearMonth ym = month == null ? YearMonth.now() : YearMonth.parse(month);
        List<ComplaintRatioStats> stats = complaintRatioService.getRanking(ym, ComplaintRatioStats.DimensionType.TENANT);
        return ApiResponse.ok(stats.stream().map(this::toResponse).toList());
    }

    private ComplaintRatioItemResponse toResponse(ComplaintRatioStats s) {
        return new ComplaintRatioItemResponse(s.getDimensionId(), null, s.getSendCount(), s.getComplaintCount(),
                s.getRatio(), Boolean.TRUE.equals(s.getOverThreshold()));
    }
}
