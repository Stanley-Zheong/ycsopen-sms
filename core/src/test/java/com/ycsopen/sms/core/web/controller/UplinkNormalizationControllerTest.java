package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.service.uplink.UplinkNormalizationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UplinkNormalizationControllerTest {
    private final UplinkNormalizationService service = mock(UplinkNormalizationService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new UplinkNormalizationController(service, users))
            .build();

    @Test
    void serializesTenantIdentityForListDetailMonitorAndBoundedOptions() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 10, 5, 10, 30);
        var uplink = new UplinkNormalizationService.UplinkRecord(
                101L, 7L, "TENANT-0007", "北斗短信", "北斗短信服务有限公司",
                "HTTP", "HTTP-API", "HTTP-UP-1", "MSG-1", "138****8000", "回复帮助", "帮助",
                "NORMALIZED", "CMCC", "北京", "北京", "10690000", 3L, 4L, "STANDARD",
                "PUSH_FAILED", 501L, now, now, now);
        var monitor = new UplinkNormalizationService.PushMonitorRow(
                501L, 7L, "TENANT-0007", "北斗短信", "北斗短信服务有限公司",
                "UPLINK:101", "UPLINK:101", "https://callback.example.com/uplink", "PUSH_FAILED",
                5, 5, 5, null, now, 60_000L);
        var option = new UplinkNormalizationService.TenantOption(
                7L, "TENANT-0007", "北斗短信", "北斗短信服务有限公司");
        when(service.adminSearch(any())).thenReturn(List.of(uplink));
        when(service.detail(101L, null)).thenReturn(uplink);
        when(service.pushMonitor(any())).thenReturn(List.of(monitor));
        when(service.tenantOptions("北斗")).thenReturn(List.of(option));

        mvc.perform(get("/api/v1/console/uplinks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].tenantId", is(7)))
                .andExpect(jsonPath("$.data[0].tenantNo", is("TENANT-0007")))
                .andExpect(jsonPath("$.data[0].tenantShortName", is("北斗短信")))
                .andExpect(jsonPath("$.data[0].tenantFullName", is("北斗短信服务有限公司")));
        mvc.perform(get("/api/v1/console/uplinks/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantNo", is("TENANT-0007")))
                .andExpect(jsonPath("$.data.tenantFullName", is("北斗短信服务有限公司")));
        mvc.perform(get("/api/v1/console/uplinks/push-monitor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].tenantShortName", is("北斗短信")))
                .andExpect(jsonPath("$.data[0].tenantNo", is("TENANT-0007")));
        mvc.perform(get("/api/v1/console/uplinks/tenant-options").param("query", "北斗"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].tenantId", is(7)))
                .andExpect(jsonPath("$.data[0].tenantNo", is("TENANT-0007")))
                .andExpect(jsonPath("$.data[0].tenantShortName", is("北斗短信")))
                .andExpect(jsonPath("$.data[0].tenantFullName", is("北斗短信服务有限公司")));
    }
}
