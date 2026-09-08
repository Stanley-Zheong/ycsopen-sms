package com.ycsopen.sms.core.common.security;

import com.ycsopen.sms.core.config.SecurityConfig;
import com.ycsopen.sms.core.service.tenant.QualificationEvidenceService;
import com.ycsopen.sms.core.service.tenant.QualificationInspectionService;
import com.ycsopen.sms.core.service.tenant.TenantReviewService;
import com.ycsopen.sms.core.service.tenant.TenantMaintenanceService;
import com.ycsopen.sms.core.domain.entity.TenantAccount;
import com.ycsopen.sms.core.web.controller.AdminTenantController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = TenantReviewSecurityTest.Application.class)
@AutoConfigureMockMvc
class TenantReviewSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @Autowired JwtAccessVerifier verifier;
    @Autowired TenantReviewService reviews;
    @Autowired QualificationEvidenceService evidence;
    @Autowired TenantMaintenanceService maintenance;

    @BeforeEach
    void setUp() {
        reset(reviews, evidence, maintenance);
        when(reviews.list()).thenReturn(List.of());
        when(maintenance.history(42)).thenReturn(List.of());
        when(maintenance.updateProfile(eq(42L), eq(7L), any(), anyString(), eq("104")))
                .thenReturn(new TenantMaintenanceService.ProfileResult(42L, "简称", "联系人",
                        "地址", 2, "经理", "软件", null, null, 8L));
        when(maintenance.changeAccountStatus(42L, 3, TenantAccount.Status.DISABLED,
                "运营停用", "105"))
                .thenReturn(new TenantMaintenanceService.AccountStatusResult(
                        42L, TenantAccount.Status.DISABLED, 4));
        when(evidence.readForReviewer(42,
                QualificationEvidenceService.EvidenceKind.BUSINESS_LICENSE, "102"))
                .thenReturn(new QualificationEvidenceService.EvidenceContent("application/pdf",
                        "safe-document".getBytes(StandardCharsets.UTF_8)));
        doAnswer(call -> {
            io.jsonwebtoken.Claims claims = call.getArgument(0);
            String subject = claims.getSubject();
            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_" + claims.get("userType", String.class)));
            if ("101".equals(subject)) authorities.add(new SimpleGrantedAuthority("tenant:read"));
            if ("102".equals(subject)) authorities.add(new SimpleGrantedAuthority("tenant:evidence:read"));
            if ("103".equals(subject)) authorities.add(new SimpleGrantedAuthority("tenant:qualification:review"));
            if ("104".equals(subject)) authorities.add(new SimpleGrantedAuthority("tenant:update"));
            if ("105".equals(subject)) authorities.add(new SimpleGrantedAuthority("tenant:status:update"));
            return new JwtAccessVerifier.VerifiedAccess(subject, authorities);
        }).when(verifier).verify(any());
    }

    @Test
    void anonymousTenantUsersAndPermissionlessOperatorsCannotReadReviewData() throws Exception {
        mvc.perform(get("/api/v1/console/admin/tenants")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/console/admin/tenants").header(HttpHeaders.AUTHORIZATION,
                bearer(200, "TENANT_ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/console/admin/tenants").header(HttpHeaders.AUTHORIZATION,
                bearer(200, "OPERATOR"))).andExpect(status().isForbidden());
        verifyNoInteractions(reviews);
    }

    @Test
    void exactPermissionsSeparateReadEvidenceAndDecisionActions() throws Exception {
        mvc.perform(get("/api/v1/console/admin/tenants").header(HttpHeaders.AUTHORIZATION,
                        bearer(101, "OPERATOR")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/v1/console/admin/tenants/42/evidence/BUSINESS_LICENSE")
                        .header(HttpHeaders.AUTHORIZATION, bearer(101, "OPERATOR")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/console/admin/tenants/42/evidence/BUSINESS_LICENSE")
                        .header(HttpHeaders.AUTHORIZATION, bearer(102, "OPERATOR")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes("safe-document".getBytes(StandardCharsets.UTF_8)));
        mvc.perform(post("/api/v1/console/admin/tenants/42/decision")
                        .header(HttpHeaders.AUTHORIZATION, bearer(102, "OPERATOR"))
                        .contentType("application/json")
                        .content("{\"expectedRevision\":7,\"decision\":\"REJECT\",\"reason\":\"不符\",\"humanConfirmed\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/console/admin/tenants/42/decision")
                        .header(HttpHeaders.AUTHORIZATION, bearer(103, "OPERATOR"))
                        .contentType("application/json")
                        .content("{\"expectedRevision\":7,\"decision\":\"REJECT\",\"reason\":\"不符\",\"humanConfirmed\":false}"))
                .andExpect(status().isOk());
        verify(reviews).decide(42, 7, TenantReviewService.Decision.REJECT, "不符", false, "103");
    }

    @Test
    void missingDecisionReturnsControlledValidationFailureWithoutCallingReviewService() throws Exception {
        mvc.perform(post("/api/v1/console/admin/tenants/42/decision")
                        .header(HttpHeaders.AUTHORIZATION, bearer(103, "OPERATOR"))
                        .contentType("application/json")
                        .content("{\"expectedRevision\":7,\"reason\":\"资料不符\",\"humanConfirmed\":false}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("INVALID_REVIEW_DECISION"));
        verify(reviews, never()).decide(anyLong(), anyLong(), any(), anyString(), anyBoolean(), anyString());
    }

    @Test
    void readProfileAndStatusPermissionsRemainExactlySeparated() throws Exception {
        mvc.perform(get("/api/v1/console/admin/tenants/42/events")
                        .header(HttpHeaders.AUTHORIZATION, bearer(101, "OPERATOR")))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/console/admin/tenants/42")
                        .header(HttpHeaders.AUTHORIZATION, bearer(104, "OPERATOR"))
                        .contentType("application/json")
                        .content("""
                                {"expectedRevision":7,"shortName":"简称","contactName":"联系人",
                                 "businessAddress":"地址","customerLevel":2,"bizManager":"经理",
                                 "industry":"软件","reason":"基础资料更新"}
                                """))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/console/admin/tenants/42/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(104, "OPERATOR"))
                        .contentType("application/json")
                        .content("{\"expectedRevision\":3,\"target\":\"DISABLED\",\"reason\":\"运营停用\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/console/admin/tenants/42")
                        .header(HttpHeaders.AUTHORIZATION, bearer(105, "OPERATOR"))
                        .contentType("application/json")
                        .content("{\"expectedRevision\":7,\"shortName\":\"简称\",\"reason\":\"更新\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/console/admin/tenants/42/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(105, "OPERATOR"))
                        .contentType("application/json")
                        .content("{\"expectedRevision\":3,\"target\":\"DISABLED\",\"reason\":\"运营停用\"}"))
                .andExpect(status().isOk());

        verify(maintenance).history(42);
        verify(maintenance).updateProfile(eq(42L), eq(7L), any(), eq("基础资料更新"), eq("104"));
        verify(maintenance).changeAccountStatus(42L, 3, TenantAccount.Status.DISABLED,
                "运营停用", "105");
    }

    @Test
    void certificationBoundFieldsCannotBeSmuggledIntoProfileMaintenance() throws Exception {
        mvc.perform(patch("/api/v1/console/admin/tenants/42")
                        .header(HttpHeaders.AUTHORIZATION, bearer(104, "OPERATOR"))
                        .contentType("application/json")
                        .content("""
                                {"expectedRevision":7,"fullName":"伪造主体名称","reason":"更新"}
                                """))
                .andExpect(status().isUnprocessableEntity());
        verify(maintenance, never()).updateProfile(anyLong(), anyLong(), any(), anyString(), anyString());
    }

    private String bearer(long subject, String userType) {
        return "Bearer " + tokens.generateToken(subject, userType, null);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
    @Import({SecurityConfig.class, AdminTenantController.class})
    static class Application {
        @Bean JwtTokenProvider tokens() {
            return new JwtTokenProvider("phase08-review-test-signing-secret-32bytes", 10);
        }
        @Bean JwtAccessVerifier verifier() { return mock(JwtAccessVerifier.class); }
        @Bean TenantReviewService reviews() { return mock(TenantReviewService.class); }
        @Bean QualificationInspectionService inspections() { return mock(QualificationInspectionService.class); }
        @Bean QualificationEvidenceService evidence() { return mock(QualificationEvidenceService.class); }
        @Bean TenantMaintenanceService maintenance() { return mock(TenantMaintenanceService.class); }
    }
}
