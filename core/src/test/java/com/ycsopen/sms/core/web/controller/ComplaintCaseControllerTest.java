package com.ycsopen.sms.core.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.common.exception.GlobalExceptionHandler;
import com.ycsopen.sms.core.common.security.logging.SecurityEventLogger;
import com.ycsopen.sms.core.service.complaint.ComplaintCaseService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ComplaintCaseControllerTest {

    private final ComplaintCaseService service = mock(ComplaintCaseService.class);
    private final SecurityEventLogger security = mock(SecurityEventLogger.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ComplaintCaseController(service))
            .setControllerAdvice(new GlobalExceptionHandler(security))
            .build();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void createsListsTransitionsRemediatesRecoversAndReturnsAnalytics() throws Exception {
        var row = new ComplaintCaseService.CaseRow(1L, "REGULATOR", 7L, 11L, 8L, 9L, "MSG-1",
                "MARKETING", "13800138000", "监管投诉", "PENDING", "COMPLETE", null, null,
                "24小时反馈", null, null, null, null, null, null, null);
        when(service.create(any(), eq("operator-auth"))).thenReturn(row);
        when(service.cases()).thenReturn(List.of(row));
        when(service.caseDetail(1L)).thenReturn(new ComplaintCaseService.CaseDetail(
                row, List.of(new ComplaintCaseService.CaseEventRow(
                10L, 1L, "REGISTERED", "operator-auth", java.time.LocalDateTime.of(2026, 9, 12, 9, 0),
                null, "PENDING", "监管投诉", null, "SUCCESS", null, null, null)), List.of()));
        when(service.referenceOptions()).thenReturn(new ComplaintCaseService.ReferenceOptions(
                List.of(new ComplaintCaseService.ReferenceOption(7L, "示例机构", null)),
                List.of(new ComplaintCaseService.ReferenceOption(11L, "移动主通道", null)),
                List.of(new ComplaintCaseService.ReferenceOption(8L, "营销签名", 7L)),
                List.of(new ComplaintCaseService.ReferenceOption(9L, "营销模板", 7L))));
        when(service.accept(eq(1L), any())).thenReturn(row.withStatus("PROCESSING"));
        when(service.handle(eq(1L), any())).thenReturn(row.withStatus("PROCESSED"));
        when(service.close(eq(1L), any())).thenReturn(row.withStatus("CLOSED"));
        when(service.remediate(eq(1L), any())).thenReturn(new ComplaintCaseService.RemediationRow(
                2L, 1L, "SUSPEND_CHANNEL", "channel:11", "APPLIED", "review-1", null, 1L));
        when(service.remediations()).thenReturn(List.of(new ComplaintCaseService.RemediationRow(
                2L, 1L, "SUSPEND_CHANNEL", "channel:11", "FAILED", "review-1", "provider timeout", 1L)));
        when(service.recover(eq(1L), any())).thenReturn(new ComplaintCaseService.RemediationRow(
                2L, 1L, "SUSPEND_CHANNEL", "channel:11", "RECOVERED", "review-2", null, 1L));
        when(service.analytics()).thenReturn(new ComplaintCaseService.AnalyticsResponse(
                1, 0, List.of(new ComplaintCaseService.TrendRow("2026-09-12", 1)),
                List.of(new ComplaintCaseService.DimensionRow("tenant:7", 1)),
                List.of(new ComplaintCaseService.DimensionRow("signature:8", 1)),
                List.of(new ComplaintCaseService.DimensionRow("MARKETING", 1))));

        mvc.perform(post("/api/v1/console/complaints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new ComplaintCaseService.CreateCommand(
                                "REGULATOR", "监管投诉", 7L, 11L, 8L, 9L, "MSG-1",
                                "MARKETING", "13800138000", "COMPLETE", "24小时反馈")))
                        .principal(new UsernamePasswordAuthenticationToken("operator-auth", "N/A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(1)));
        mvc.perform(get("/api/v1/console/complaints"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].attributionQuality", is("COMPLETE")));
        mvc.perform(get("/api/v1/console/complaints/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.complaint.id", is(1)))
                .andExpect(jsonPath("$.data.timeline[0].eventType", is("REGISTERED")))
                .andExpect(jsonPath("$.data.timeline[0].actor", is("operator-auth")));
        mvc.perform(get("/api/v1/console/complaint-reference-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenants[0].label", is("示例机构")))
                .andExpect(jsonPath("$.data.channels[0].tenantId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.signatures[0].tenantId", is(7)));
        mvc.perform(post("/api/v1/console/complaints/1/accept").contentType(MediaType.APPLICATION_JSON)
                        .principal(new UsernamePasswordAuthenticationToken("operator-auth", "N/A"))
                        .content(json.writeValueAsString(new ComplaintCaseService.StateCommand(null, "接单", null, null, "forged"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PROCESSING")));
        mvc.perform(post("/api/v1/console/complaints/1/handle").contentType(MediaType.APPLICATION_JSON)
                        .principal(new UsernamePasswordAuthenticationToken("operator-auth", "N/A"))
                        .content(json.writeValueAsString(new ComplaintCaseService.StateCommand(null, "属实", "暂停通道", "整改", "forged"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PROCESSED")));
        mvc.perform(post("/api/v1/console/complaints/1/close").contentType(MediaType.APPLICATION_JSON)
                        .principal(new UsernamePasswordAuthenticationToken("operator-auth", "N/A"))
                        .content(json.writeValueAsString(new ComplaintCaseService.StateCommand(null, "复核关闭", null, null, "forged"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CLOSED")));
        mvc.perform(post("/api/v1/console/complaints/1/remediations").contentType(MediaType.APPLICATION_JSON)
                        .principal(new UsernamePasswordAuthenticationToken("operator-auth", "N/A"))
                        .content(json.writeValueAsString(new ComplaintCaseService.RemediationCommand(
                                "SUSPEND_CHANNEL", "channel:11", "forged", "review-1", "投诉集中"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("APPLIED")));
        mvc.perform(get("/api/v1/console/complaint-remediations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status", is("FAILED")))
                .andExpect(jsonPath("$.data[0].failureReason", is("provider timeout")));
        mvc.perform(post("/api/v1/console/complaints/1/recoveries").contentType(MediaType.APPLICATION_JSON)
                        .principal(new UsernamePasswordAuthenticationToken("operator-auth", "N/A"))
                        .content(json.writeValueAsString(new ComplaintCaseService.RecoveryCommand(
                                2L, "review-2", "forged", "补偿完成"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("RECOVERED")));
        mvc.perform(get("/api/v1/console/complaint-analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trend[0].day", is("2026-09-12")))
                .andExpect(jsonPath("$.data.byTenant[0].dimension", is("tenant:7")));

        verify(service).create(any(), eq("operator-auth"));
        verify(service).accept(eq(1L), eq(new ComplaintCaseService.StateCommand(null, "接单", null, null, "operator-auth")));
        verify(service).remediate(eq(1L), eq(new ComplaintCaseService.RemediationCommand(
                "SUSPEND_CHANNEL", "channel:11", "operator-auth", "review-1", "投诉集中")));
    }

    @Test
    void staleBusinessFailureReturnsConflictWithStableCode() throws Exception {
        when(service.accept(eq(1L), any())).thenThrow(
                new BusinessException("COMPLAINT_STATE_STALE", "投诉状态已变化，请刷新后重试"));

        mvc.perform(post("/api/v1/console/complaints/1/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .principal(new UsernamePasswordAuthenticationToken("operator-auth", "N/A"))
                        .content(json.writeValueAsString(new ComplaintCaseService.StateCommand(
                                null, "受理", null, null, "forged"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is(409)))
                .andExpect(jsonPath("$.data.errorCode", is("COMPLAINT_STATE_STALE")));
    }
}
