package com.ycsopen.sms.core.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.domain.entity.User;
import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.service.report.CustomReportService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

class CustomReportControllerTest {
    private final CustomReportService service = mock(CustomReportService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CustomReportController(service, users)).build();
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void exposesCapabilitiesPreviewSaveDefinitionsAndExportRequests() throws Exception {
        var command = new CustomReportService.ReportCommand("通道日报", "CHANNEL_DELIVERY",
                List.of("period", "tenant_id"), List.of("send_count"), 7L, 11L, null, null,
                LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 9, 2, 0, 0), "PLATFORM");
        when(service.capabilities()).thenReturn(List.of(new CustomReportService.CapabilityRow(
                "CHANNEL_DELIVERY", "通道发送成功成本延迟指标", List.of("period", "tenant_id"),
                List.of("send_count"), "send/success/failure", "freshness", "PLATFORM", "v1")));
        when(service.preview(any(), any())).thenReturn(new CustomReportService.PreviewResult(
                "CHANNEL_DELIVERY", "通道发送成功成本延迟指标", "send/success/failure", "v1", "freshness",
                LocalDateTime.of(2026, 9, 1, 1, 3), "FRESH", List.of("period", "tenant_id", "send_count"),
                false,
                List.of(new CustomReportService.ReportDataRow(Map.of("period", "2026-09-01", "tenant_id", 7L,
                        "send_count", 20), "drill-43", "FRESH", LocalDateTime.of(2026, 9, 1, 1, 3)))));
        when(service.save(any(), any())).thenReturn(new CustomReportService.DefinitionRow(5L, "通道日报",
                "CHANNEL_DELIVERY", 7L, "PLATFORM", "[\"period\"]", "[\"send_count\"]", "{}",
                "{\"formulaVersion\":\"v1\"}", "ACTIVE", "finance", LocalDateTime.of(2026, 9, 1, 2, 0)));
        when(service.definitions(any())).thenReturn(List.of(new CustomReportService.DefinitionRow(5L, "通道日报",
                "CHANNEL_DELIVERY", 7L, "PLATFORM", "[\"period\"]", "[\"send_count\"]", "{}",
                "{\"formulaVersion\":\"v1\"}", "ACTIVE", "finance", LocalDateTime.of(2026, 9, 1, 2, 0))));
        when(service.requestExport(eq(5L), any())).thenReturn(new CustomReportService.ExportRequestRow(6L, 5L,
                "{\"formulaVersion\":\"v1\"}", "REQUESTED", "finance", LocalDateTime.of(2026, 9, 1, 2, 1)));

        mvc.perform(get("/api/v1/console/custom-reports/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].metricCode", is("CHANNEL_DELIVERY")));
        mvc.perform(post("/api/v1/console/custom-reports/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .principal(platformAuth())
                        .content(json.writeValueAsString(command)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.formulaVersion", is("v1")))
                .andExpect(jsonPath("$.data.rows[0].drilldownKey", is("drill-43")));
        mvc.perform(post("/api/v1/console/custom-reports/definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .principal(platformAuth())
                        .content(json.writeValueAsString(command)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.definitionSnapshot", is("{\"formulaVersion\":\"v1\"}")));
        mvc.perform(get("/api/v1/console/custom-reports/definitions")
                        .principal(platformAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id", is(5)));
        mvc.perform(post("/api/v1/console/custom-reports/definitions/5/export")
                        .principal(platformAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("REQUESTED")));

        verify(service).preview(any(), eq(CustomReportService.Actor.platform("43")));
        verify(service).save(any(), eq(CustomReportService.Actor.platform("43")));
        verify(service).requestExport(eq(5L), eq(CustomReportService.Actor.platform("43")));
    }

    @Test
    void derivesTenantActorFromAuthenticatedUserWhenRoleIsTenantScoped() throws Exception {
        var tenant = new User();
        tenant.setId(88L);
        tenant.setTenantId(7L);
        when(users.findById(88L)).thenReturn(Optional.of(tenant));
        when(service.preview(any(), any())).thenReturn(new CustomReportService.PreviewResult(
                "TENANT_BEHAVIOR", "租户发送消费活跃指标", "accepted/rejected/send/success", "v1", "freshness",
                LocalDateTime.of(2026, 9, 1, 1, 3), "FRESH", List.of("period", "tenant_id", "submit_count"),
                false, List.of()));

        mvc.perform(post("/api/v1/console/custom-reports/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .principal(new UsernamePasswordAuthenticationToken("88", "N/A",
                                List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"))))
                        .content(json.writeValueAsString(new CustomReportService.ReportCommand("租户报表",
                                "TENANT_BEHAVIOR", List.of("period", "tenant_id"),
                                List.of("submit_count"), 7L, null, null, null,
                                LocalDateTime.of(2026, 9, 1, 0, 0),
                                LocalDateTime.of(2026, 9, 2, 0, 0), "TENANT"))))
                .andExpect(status().isOk());

        verify(service).preview(any(), eq(CustomReportService.Actor.tenant("88", 7L)));
    }

    private static UsernamePasswordAuthenticationToken platformAuth() {
        return new UsernamePasswordAuthenticationToken("43", "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_FINANCE")));
    }
}
