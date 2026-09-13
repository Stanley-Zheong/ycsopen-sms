package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.service.complaint.ComplaintRatioDashboardService;
import com.ycsopen.sms.core.service.complaint.ComplaintRatioDashboardService.ComplaintCaseRow;
import com.ycsopen.sms.core.service.complaint.ComplaintRatioDashboardService.InterventionCommand;
import com.ycsopen.sms.core.service.complaint.ComplaintRatioDashboardService.InterventionResult;
import com.ycsopen.sms.core.service.complaint.ComplaintRatioDashboardService.RatioRow;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeParseException;
import java.time.YearMonth;
import java.util.List;

/** F-11.9 仪表盘"通道/机构月度投诉占比看板"数据接口。 */
@RestController
@RequestMapping("/api/v1/console/dashboard")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'FINANCE')")
public class DashboardController {

    private final ComplaintRatioDashboardService complaintRatioDashboardService;

    public DashboardController(ComplaintRatioDashboardService complaintRatioDashboardService) {
        this.complaintRatioDashboardService = complaintRatioDashboardService;
    }

    @GetMapping("/complaint-ratio/channel")
    public ApiResponse<List<RatioRow>> channelRanking(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer topN,
            @RequestParam(defaultValue = "false") boolean all) {
        return ApiResponse.ok(complaintRatioDashboardService.ranking("CHANNEL", parseMonth(month), topN, all));
    }

    @GetMapping("/complaint-ratio/tenant")
    public ApiResponse<List<RatioRow>> tenantRanking(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer topN,
            @RequestParam(defaultValue = "false") boolean all) {
        return ApiResponse.ok(complaintRatioDashboardService.ranking("TENANT", parseMonth(month), topN, all));
    }

    @GetMapping("/complaint-ratio/{dimension}/{dimensionId}/complaints")
    public ApiResponse<List<ComplaintCaseRow>> drilldown(
            @PathVariable String dimension,
            @PathVariable long dimensionId,
            @RequestParam(required = false) String month) {
        return ApiResponse.ok(complaintRatioDashboardService.drilldown(parseDimension(dimension), dimensionId, parseMonth(month)));
    }

    @PostMapping("/complaint-ratio/{dimension}/{dimensionId}/pause")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<InterventionResult> pause(
            @PathVariable String dimension,
            @PathVariable long dimensionId,
            @RequestParam(required = false) String month,
            @RequestBody(required = false) InterventionCommand command,
            Authentication authentication) {
        return ApiResponse.ok(complaintRatioDashboardService.pause(parseDimension(dimension), dimensionId, parseMonth(month), command, actor(authentication)));
    }

    private static YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException failure) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "month must be yyyy-MM");
        }
    }

    private static String parseDimension(String dimension) {
        if (dimension == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dimension is required");
        }
        return switch (dimension.trim().toLowerCase()) {
            case "channel" -> "channel";
            case "tenant" -> "tenant";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dimension must be channel or tenant");
        };
    }

    private static String actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "console";
        }
        return authentication.getName().trim();
    }
}
