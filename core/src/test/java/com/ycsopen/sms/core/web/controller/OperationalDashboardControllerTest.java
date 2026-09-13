package com.ycsopen.sms.core.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.domain.entity.User;
import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.service.dashboard.OperationalDashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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

class OperationalDashboardControllerTest {
    private final OperationalDashboardService service = mock(OperationalDashboardService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new OperationalDashboardController(service, users)).build();
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void exposesPlatformDashboardResourcesStatusAndConfiguration() throws Exception {
        var source = new OperationalDashboardService.MetricSource("statistics_aggregates", "success_count/send_count",
                LocalDateTime.of(2026, 9, 10, 9, 5), "PLATFORM", "v1");
        when(service.platformDashboard()).thenReturn(new OperationalDashboardService.PlatformDashboard(
                new OperationalDashboardService.RealtimeCards(3, 150, new BigDecimal("0.9000"), 1, 150),
                new OperationalDashboardService.KpiCards(150, 1, new BigDecimal("0.9000"), new BigDecimal("1.2300"), "success_count/send_count"),
                List.of(new OperationalDashboardService.HourlyTrendRow(LocalDateTime.of(2026, 9, 10, 9, 0), 100, 90, new BigDecimal("0.9000"))),
                List.of(new OperationalDashboardService.TenantRankRow(7L, 100, 90, new BigDecimal("0.9000"))),
                new OperationalDashboardService.ChannelHealth(1, 1, 1),
                new OperationalDashboardService.FinanceWarning(1, LocalDateTime.of(2026, 9, 10, 9, 6)),
                source));
        when(service.resourceStatistics(any(), eq(null))).thenReturn(new OperationalDashboardService.ResourceStatistics(
                List.of(new OperationalDashboardService.ResourceRow(7L, 55L, 66L, 100, 90, 5, LocalDateTime.of(2026, 9, 10, 9, 4))),
                List.of(new OperationalDashboardService.ChannelComparisonRow(7L, 11L, 100, 90, 10, new BigDecimal("0.9000"), LocalDateTime.of(2026, 9, 10, 9, 5))),
                List.of("tenant_id", "signature_id", "template_id", "success_count"), source, false, "NONE"));
        when(service.apiStatus()).thenReturn(new OperationalDashboardService.ApiStatus(
                List.of(new OperationalDashboardService.HealthRow("DATABASE", "NORMAL", "statistics_aggregates",
                        LocalDateTime.of(2026, 9, 10, 9, 5), "dashboard query source", "health|database|statistics_aggregates")),
                new OperationalDashboardService.MetricSource("operational_source_tables", "live table counts",
                        LocalDateTime.of(2026, 9, 10, 9, 5), "PLATFORM", "v1")));
        when(service.configuration("ADMIN")).thenReturn(new OperationalDashboardService.DashboardConfiguration(
                "ADMIN", true, true, "MANUAL", 300, "0.0030", "system", null));
        when(service.saveConfiguration(any(), eq("43"))).thenReturn(new OperationalDashboardService.DashboardConfiguration(
                "ADMIN", true, true, "POLLING", 300, "0.0030", "43", LocalDateTime.of(2026, 9, 10, 10, 0)));

        mvc.perform(get("/api/v1/console/operational-dashboards/platform").principal(platformAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.realtime.todayMessages", is(150)))
                .andExpect(jsonPath("$.data.source.registry", is("statistics_aggregates")));
        mvc.perform(get("/api/v1/console/operational-dashboards/resource-statistics").principal(platformAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resources[0].signatureId", is(55)));
        mvc.perform(get("/api/v1/console/operational-dashboards/api-status").principal(platformAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows[0].drilldownKey", is("health|database|statistics_aggregates")));
        mvc.perform(get("/api/v1/console/operational-dashboards/configuration").param("role", "ADMIN").principal(platformAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role", is("ADMIN")));
        mvc.perform(post("/api/v1/console/operational-dashboards/configuration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .principal(platformAuth())
                        .content(json.writeValueAsString(new OperationalDashboardService.DashboardConfigurationCommand(
                                "ADMIN", true, true, "POLLING", 300, "0.0030"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refreshMode", is("POLLING")));

        verify(service).resourceStatistics(eq(OperationalDashboardService.Actor.platform("43")), eq(null));
    }

    @Test
    void derivesTenantOverviewActorFromAuthenticatedTenantUser() throws Exception {
        User tenant = new User();
        tenant.setId(88L);
        tenant.setTenantId(7L);
        when(users.findById(88L)).thenReturn(Optional.of(tenant));
        when(service.tenantOverview(any(), eq(null))).thenReturn(new OperationalDashboardService.TenantOverview(
                7L, 120000, "TRIAL", "ACTIVE", 100, new BigDecimal("0.9000"), "NORMAL",
                new OperationalDashboardService.MetricSource("statistics_aggregates", "success_count/send_count",
                        LocalDateTime.of(2026, 9, 10, 9, 5), "TENANT", "v1")));

        mvc.perform(get("/api/v1/console/operational-dashboards/tenant-overview")
                        .principal(new UsernamePasswordAuthenticationToken("88", "N/A",
                                List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantId", is(7)));

        verify(service).tenantOverview(eq(OperationalDashboardService.Actor.tenant("88", 7L)), eq(null));
    }

    private static UsernamePasswordAuthenticationToken platformAuth() {
        return new UsernamePasswordAuthenticationToken("43", "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_FINANCE")));
    }
}
