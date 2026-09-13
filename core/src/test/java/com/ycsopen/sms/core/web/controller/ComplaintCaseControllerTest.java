package com.ycsopen.sms.core.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ComplaintCaseController(service)).build();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void createsListsTransitionsRemediatesRecoversAndReturnsAnalytics() throws Exception {
        var row = new ComplaintCaseService.CaseRow(1L, "REGULATOR", 7L, 11L, 8L, 9L, "MSG-1",
                "MARKETING", "13800138000", "监管投诉", "PENDING", "COMPLETE", null, null,
                "24小时反馈", null, null, null, null, null, null, null);
        when(service.create(any(), eq("operator-auth"))).thenReturn(row);
        when(service.cases()).thenReturn(List.of(row));
        when(service.accept(eq(1L), any())).thenReturn(row.withStatus("PROCESSING"));
        when(service.handle(eq(1L), any())).thenReturn(row.withStatus("PROCESSED"));
        when(service.close(eq(1L), any())).thenReturn(row.withStatus("CLOSED"));
        when(service.remediate(eq(1L), any())).thenReturn(new ComplaintCaseService.RemediationRow(
                2L, 1L, "SUSPEND_CHANNEL", "channel:11", "APPLIED", "review-1", null, 1L));
        when(service.recover(eq(1L), any())).thenReturn(new ComplaintCaseService.RemediationRow(
                2L, 1L, "SUSPEND_CHANNEL", "channel:11", "RECOVERED", "review-2", null, 1L));
        when(service.analytics()).thenReturn(new ComplaintCaseService.AnalyticsResponse(
                1, 0, List.of(new ComplaintCaseService.DimensionRow("tenant:7", 1)),
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
        mvc.perform(post("/api/v1/console/complaints/1/recoveries").contentType(MediaType.APPLICATION_JSON)
                        .principal(new UsernamePasswordAuthenticationToken("operator-auth", "N/A"))
                        .content(json.writeValueAsString(new ComplaintCaseService.RecoveryCommand(
                                2L, "review-2", "forged", "补偿完成"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("RECOVERED")));
        mvc.perform(get("/api/v1/console/complaint-analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.byTenant[0].dimension", is("tenant:7")));

        verify(service).create(any(), eq("operator-auth"));
        verify(service).accept(eq(1L), eq(new ComplaintCaseService.StateCommand(null, "接单", null, null, "operator-auth")));
        verify(service).remediate(eq(1L), eq(new ComplaintCaseService.RemediationCommand(
                "SUSPEND_CHANNEL", "channel:11", "operator-auth", "review-1", "投诉集中")));
    }
}
