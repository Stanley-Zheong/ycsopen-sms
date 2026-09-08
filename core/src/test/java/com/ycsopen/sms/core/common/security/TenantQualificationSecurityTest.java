package com.ycsopen.sms.core.common.security;

import com.ycsopen.sms.core.config.SecurityConfig;
import com.ycsopen.sms.core.service.tenant.*;
import com.ycsopen.sms.core.web.controller.*;
import com.ycsopen.sms.core.web.dto.TenantRegistrationResponse;
import com.ycsopen.sms.core.domain.entity.Tenant;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = TenantQualificationSecurityTest.Application.class)
@AutoConfigureMockMvc
class TenantQualificationSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @Autowired JwtAccessVerifier verifier;
    @Autowired TenantRegistrationService registrations;
    @BeforeEach void setup() {
        doAnswer(call -> {
            io.jsonwebtoken.Claims claims = call.getArgument(0);
            return new JwtAccessVerifier.VerifiedAccess(claims.getSubject(), java.util.List.of(new SimpleGrantedAuthority("ROLE_" + claims.get("userType", String.class))));
        }).when(verifier).verify(any());
        Tenant tenant = new Tenant(); tenant.setId(42L); tenant.setTenantNo("T42");
        when(registrations.statusOwn("81")).thenReturn(TenantRegistrationResponse.from(tenant));
    }
    @Test void authenticatedTenantAdminCanReadOwnQualification() throws Exception {
        mvc.perform(get("/api/v1/console/tenant/qualification").header("Authorization", "Bearer " + tokens.generateToken(81L, "TENANT_ADMIN", 42L)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.tenantId").value(42));
    }
    @Test void anonymousAndOtherRolesCannotUseTheTenantRoute() throws Exception {
        mvc.perform(get("/api/v1/console/tenant/qualification")).andExpect(status().isUnauthorized());
        for (String role : new String[]{"ADMIN", "OPERATOR", "FINANCE", "TENANT_USER"}) {
            mvc.perform(get("/api/v1/console/tenant/qualification").header("Authorization", "Bearer " + tokens.generateToken(81L, role, 42L))).andExpect(status().isForbidden());
        }
    }

    @Test void publicRegistrationCanReachTheExistingCapabilityProtectedUploadHandshake() throws Exception {
        mvc.perform(post("/api/v1/console/tenants/registration-object-sessions"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/console/tenants"))
                .andExpect(status().isUnauthorized());
    }
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
    @Import({SecurityConfig.class, TenantRegistrationController.class, TenantQualificationController.class, TenantQualificationExceptionHandler.class})
    static class Application {
        @Bean JwtTokenProvider tokens() { return new JwtTokenProvider("phase08-test-signing-secret-at-least-32-bytes", 10); }
        @Bean JwtAccessVerifier verifier() { return mock(JwtAccessVerifier.class); }
        @Bean TenantRegistrationService registrations() { return mock(TenantRegistrationService.class); }
        @Bean ContactVerificationService contacts() { return mock(ContactVerificationService.class); }
        @Bean com.ycsopen.sms.core.common.security.object.TenantRegistrationObjectSessionService objectSessions() {
            return mock(com.ycsopen.sms.core.common.security.object.TenantRegistrationObjectSessionService.class);
        }
        @Bean TenantRegistrationObjectController uploadController(com.ycsopen.sms.core.common.security.object.TenantRegistrationObjectSessionService sessions) {
            return new TenantRegistrationObjectController(sessions);
        }
    }
}
