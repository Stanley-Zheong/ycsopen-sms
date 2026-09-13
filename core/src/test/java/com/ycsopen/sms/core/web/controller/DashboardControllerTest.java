package com.ycsopen.sms.core.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.service.complaint.ComplaintRatioDashboardService;
import com.ycsopen.sms.core.service.complaint.ComplaintRatioDashboardService.ComplaintCaseRow;
import com.ycsopen.sms.core.service.complaint.ComplaintRatioDashboardService.InterventionCommand;
import com.ycsopen.sms.core.service.complaint.ComplaintRatioDashboardService.InterventionResult;
import com.ycsopen.sms.core.service.complaint.ComplaintRatioDashboardService.RatioRow;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
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

class DashboardControllerTest {
    private final ComplaintRatioDashboardService service = mock(ComplaintRatioDashboardService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new DashboardController(service)).build();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void returnsComplaintRatioRowsWithThresholdFreshnessAndQuality() throws Exception {
        when(service.ranking("CHANNEL", YearMonth.of(2026, 8), 5, false)).thenReturn(List.of(new RatioRow(
                "2026-08", "CHANNEL", 11L, "移动主通道", 1000L, 3L,
                new BigDecimal("0.003000"), new BigDecimal("0.003"), "default-v1",
                true, "COMPLETE", "BREACHED", "complaint_ratio_stats:message_tasks:complaints",
                "T_PLUS_1_DAILY", LocalDateTime.of(2026, 8, 2, 1, 0), 1,
                true, "complaint-ratio:CHANNEL:11:2026-08:default-v1")));

        mvc.perform(get("/api/v1/console/dashboard/complaint-ratio/channel")
                        .param("month", "2026-08")
                        .param("topN", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].dimensionName", is("移动主通道")))
                .andExpect(jsonPath("$.data[0].thresholdResult", is("BREACHED")))
                .andExpect(jsonPath("$.data[0].dataQuality", is("COMPLETE")))
                .andExpect(jsonPath("$.data[0].freshnessPolicy", is("T_PLUS_1_DAILY")));
    }

    @Test
    void drillsDownAndPausesWithAuthenticatedActor() throws Exception {
        when(service.drilldown("channel", 11L, YearMonth.of(2026, 8))).thenReturn(List.of(new ComplaintCaseRow(
                1L, "REGULATOR", 7L, 11L, "MSG-1", "监管投诉", "PENDING", "COMPLETE",
                LocalDateTime.of(2026, 8, 2, 10, 0))));
        when(service.pause(eq("channel"), eq(11L), eq(YearMonth.of(2026, 8)), any(), eq("operator-auth")))
                .thenReturn(new InterventionResult("CHANNEL", 11L, "PAUSED", 9L, null,
                        "complaint-ratio:CHANNEL:11:2026-08:default-v1", "PAUSE"));

        mvc.perform(get("/api/v1/console/dashboard/complaint-ratio/channel/11/complaints")
                        .param("month", "2026-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].messageId", is("MSG-1")));
        mvc.perform(post("/api/v1/console/dashboard/complaint-ratio/channel/11/pause")
                        .param("month", "2026-08")
                        .principal(new UsernamePasswordAuthenticationToken("operator-auth", "N/A"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new InterventionCommand("投诉率超阈值", "review-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PAUSED")))
                .andExpect(jsonPath("$.data.sourceKey", is("complaint-ratio:CHANNEL:11:2026-08:default-v1")));

        verify(service).pause(eq("channel"), eq(11L), eq(YearMonth.of(2026, 8)),
                eq(new InterventionCommand("投诉率超阈值", "review-1")), eq("operator-auth"));
    }

    @Test
    void rejectsInvalidComplaintRatioMonthAndDimensionAsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/console/dashboard/complaint-ratio/channel")
                        .param("month", "2026-99"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/console/dashboard/complaint-ratio/provider/11/complaints")
                        .param("month", "2026-08"))
                .andExpect(status().isBadRequest());
    }
}
