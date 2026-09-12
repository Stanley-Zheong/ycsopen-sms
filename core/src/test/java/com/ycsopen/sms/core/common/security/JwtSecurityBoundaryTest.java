package com.ycsopen.sms.core.common.security;

import com.ycsopen.sms.core.config.SecurityConfig;
import com.ycsopen.sms.core.service.account.IdentitySessionService;
import com.ycsopen.sms.core.web.controller.SessionController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@SpringBootTest(
        classes = JwtSecurityBoundaryTest.TestApplication.class,
        properties = "management.health.redis.enabled=false")
@AutoConfigureMockMvc
class JwtSecurityBoundaryTest {

    private static final String SIGNING_SECRET = "phase-05-jwt-test-secret-is-at-least-32-bytes";
    private static final String FORGED_SECRET = "phase-05-forged-secret-is-also-32-bytes";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTokenProvider tokens;

    @Autowired
    private JwtAccessVerifier accessVerifier;

    @Autowired
    private IdentitySessionService sessions;

    @BeforeEach
    void authorizeCurrentTestUser() {
        doAnswer(invocation -> {
            io.jsonwebtoken.Claims claims = invocation.getArgument(0);
            String claimedType = claims.get("userType", String.class);
            return new JwtAccessVerifier.VerifiedAccess(claims.getSubject(), java.util.List.of(
                    new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + claimedType)));
        }).when(accessVerifier).verify(any());
    }

    @Test
    void publicAuthenticationAndHealthBoundariesRemainAccessible() throws Exception {
        mvc.perform(get("/api/v1/auth/ping"))
                .andExpect(status().isOk());
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void consoleBoundaryRejectsMissingToken() throws Exception {
        mvc.perform(get("/api/v1/console/ping"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void consoleBoundaryAcceptsValidTokenAndExposesAuthenticatedSubject() throws Exception {
        String token = tokens.generateToken(42L, "ADMIN", null);

        mvc.perform(get("/api/v1/console/ping")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("42"));
    }

    @Test
    void consoleBoundaryRejectsTokenSignedWithAnotherKey() throws Exception {
        String token = new JwtTokenProvider(FORGED_SECRET, 10)
                .generateToken(42L, "ADMIN", null);

        mvc.perform(get("/api/v1/console/ping")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void consoleBoundaryRejectsExpiredToken() throws Exception {
        String token = new JwtTokenProvider(SIGNING_SECRET, -1)
                .generateToken(42L, "ADMIN", null);

        mvc.perform(get("/api/v1/console/ping")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void consoleBoundaryRejectsTenantPrincipal() throws Exception {
        String token = tokens.generateToken(42L, "TENANT_USER", 7L);

        mvc.perform(get("/api/v1/console/ping")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void tenantPrincipalCanRevokeItsExactDurableSessionOnLogout() throws Exception {
        JwtTokenProvider.IssuedToken issued = tokens.issueToken(42L, "TENANT_USER", 7L);

        mvc.perform(post("/api/v1/console/session/logout")
                        .header("Authorization", "Bearer " + issued.token()))
                .andExpect(status().isOk());

        verify(sessions).revoke(issued.sessionId(), 42L);
    }

    @Test
    void smsHmacBoundaryIsNotClaimedByJwtAuthentication() throws Exception {
        mvc.perform(get("/api/v1/sms/ping")
                        .header("Authorization", "Bearer definitely-not-a-jwt"))
                .andExpect(status().isOk());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class
    })
    @Import({SecurityConfig.class, BoundaryController.class, SessionController.class})
    static class TestApplication {

        @Bean
        JwtTokenProvider jwtTokenProvider() {
            return new JwtTokenProvider(SIGNING_SECRET, 10);
        }

        @Bean
        JwtAccessVerifier jwtAccessVerifier() {
            return org.mockito.Mockito.mock(JwtAccessVerifier.class);
        }

        @Bean
        IdentitySessionService identitySessionService() {
            return org.mockito.Mockito.mock(IdentitySessionService.class);
        }
    }

    @RestController
    static class BoundaryController {

        @GetMapping({"/api/v1/auth/ping", "/actuator/health", "/api/v1/sms/ping"})
        String publicPing() {
            return "ok";
        }

        @GetMapping("/api/v1/console/ping")
        String consolePing(java.security.Principal principal) {
            return principal == null ? "anonymous" : principal.getName();
        }
    }
}
