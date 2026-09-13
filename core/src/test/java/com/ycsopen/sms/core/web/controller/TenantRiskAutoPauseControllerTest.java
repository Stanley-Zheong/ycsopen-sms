package com.ycsopen.sms.core.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.service.risk.TenantRiskAutoPauseService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
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

class TenantRiskAutoPauseControllerTest {
    private final TenantRiskAutoPauseService service = mock(TenantRiskAutoPauseService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new TenantRiskAutoPauseController(service)).build();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void exposesRulesEvaluationEpisodesAndRecoveryWithAuthenticatedActor() throws Exception {
        var rule = new TenantRiskAutoPauseService.RuleRow(1L, "失败率封停", 7L, "FAILURE_RATE",
                new BigDecimal("0.2000"), 15, "AUTO_SUSPEND", "[\"ops\"]", "ACTIVE", null);
        var result = new TenantRiskAutoPauseService.EvaluationResult(2L, "COMPLETE", new BigDecimal("0.250000"), true);
        var episode = new TenantRiskAutoPauseService.EpisodeRow(2L, 7L, 1L, 3L, "FAILURE_RATE",
                "failure-window-7", "statistics_aggregates", 25L, 100L, new BigDecimal("0.250000"),
                new BigDecimal("0.200000"), 15, "COMPLETE", "AUTO_SUSPEND", "PAUSED",
                "SIGNED", "numerator=25,denominator=100", "review-42", "operator");
        when(service.saveRule(any(), eq("operator-auth"))).thenReturn(rule);
        when(service.rules(7L)).thenReturn(List.of(rule));
        when(service.evaluate(any(), eq("operator-auth"))).thenReturn(result);
        when(service.episodes(7L)).thenReturn(List.of(episode));
        when(service.recover(eq(2L), any(), eq("operator-auth"))).thenReturn(episode.withStatus("RESOLVED"));

        mvc.perform(post("/api/v1/console/tenant-risk/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .principal(new UsernamePasswordAuthenticationToken("operator-auth", "N/A"))
                        .content(json.writeValueAsString(new TenantRiskAutoPauseService.RuleCommand(
                                "失败率封停", 7L, "FAILURE_RATE", new BigDecimal("0.2000"), 15,
                                "AUTO_SUSPEND", "[\"ops\"]", "ACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ruleName", is("失败率封停")));
        mvc.perform(get("/api/v1/console/tenant-risk/rules?tenantId=7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].metric", is("FAILURE_RATE")));
        mvc.perform(post("/api/v1/console/tenant-risk/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .principal(new UsernamePasswordAuthenticationToken("operator-auth", "N/A"))
                        .content(json.writeValueAsString(new TenantRiskAutoPauseService.EvaluationCommand(
                                7L, "FAILURE_RATE", 25L, 100L, 15, "failure-window-7", "statistics_aggregates"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paused", is(true)));
        mvc.perform(get("/api/v1/console/tenant-risk/episodes?tenantId=7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sourceSnapshot", is("numerator=25,denominator=100")));
        mvc.perform(post("/api/v1/console/tenant-risk/episodes/2/recover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .principal(new UsernamePasswordAuthenticationToken("operator-auth", "N/A"))
                        .content(json.writeValueAsString(new TenantRiskAutoPauseService.RecoveryCommand(
                                "review-42", "复核通过"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("RESOLVED")));

        verify(service).saveRule(any(), eq("operator-auth"));
        verify(service).evaluate(any(), eq("operator-auth"));
        verify(service).recover(eq(2L), eq(new TenantRiskAutoPauseService.RecoveryCommand("review-42", "复核通过")), eq("operator-auth"));
    }
}
