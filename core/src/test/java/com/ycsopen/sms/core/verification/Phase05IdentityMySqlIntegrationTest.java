package com.ycsopen.sms.core.verification;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.common.security.JwtTokenProvider;
import com.ycsopen.sms.core.domain.entity.User;
import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.service.account.AuthService;
import com.ycsopen.sms.core.service.account.IdentitySessionService;
import com.ycsopen.sms.core.service.account.LoginAnomalyService;
import com.ycsopen.sms.core.service.audit.SecurityEventService;
import com.ycsopen.sms.core.web.dto.LoginRequest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Executes the complete successful-login write path against the Flyway-migrated MySQL schema. */
@SpringBootTest(
        classes = Phase05IdentityMySqlIntegrationTest.IdentityVerificationApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("phase01-integration")
@EnabledIfSystemProperty(named = "phase01.integration.enabled", matches = "true")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class Phase05IdentityMySqlIntegrationTest {

    private static Phase01ServiceSession mysql;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        mysql = Phase01ServiceHarness.startMySql();
        registry.add("spring.datasource.url", mysql::jdbcUrl);
        registry.add("spring.datasource.username", mysql::username);
        registry.add("spring.datasource.password", mysql::password);
    }

    @AfterAll
    static void stopMySql() {
        if (mysql != null) {
            mysql.close();
        }
    }

    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtTokenProvider tokens;
    @Autowired IdentitySessionService sessions;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM security_events");
        jdbc.update("DELETE FROM identity_notification_outbox");
        jdbc.update("DELETE FROM login_history");
        jdbc.update("DELETE FROM user_sessions");
        users.deleteAll();
    }

    @Test
    void successfulLoginWritesSessionHistoryAndUnusualLoginOutboxToMigratedSchema() {
        User user = new User();
        user.setUsername("admin");
        user.setPasswordHash(passwords.encode("Correct123"));
        user.setUserType(User.UserType.ADMIN);
        user.setStatus(User.UserStatus.ACTIVE);
        user.setFailedLoginCount(0);
        user.setLastLoginIp("192.0.2.10");
        user = users.saveAndFlush(user);

        Instant expiry = Instant.now().plusSeconds(3600);
        when(tokens.issueToken(user.getId(), "ADMIN", null))
                .thenReturn(new JwtTokenProvider.IssuedToken(
                        "signed-token", "phase05-session", expiry));

        var response = auth.login(
                new LoginRequest("admin", "Correct123"), "192.0.2.11", "Chrome/152");

        assertThat(response.accessToken()).isEqualTo("signed-token");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_sessions
                WHERE id = 'phase05-session' AND user_id = ? AND user_type = 'ADMIN'
                  AND login_ip = '192.0.2.11' AND user_agent = 'Chrome/152'
                  AND is_abnormal_login = 1 AND revoked_at IS NULL
                """, Integer.class, user.getId())).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM login_history
                WHERE user_id = ? AND username = 'admin' AND outcome = 'SUCCESS_UNUSUAL'
                """, Integer.class, user.getId())).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM identity_notification_outbox
                WHERE target_user_id = ? AND event_type = 'UNUSUAL_LOGIN'
                  AND source_ref = 'phase05-session' AND status = 'PENDING'
                """, Integer.class, user.getId())).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM security_events
                WHERE actor_user_id = ? AND event_type = 'UNUSUAL_LOGIN'
                  AND result_code = 'DETECTED'
                """, Integer.class, user.getId())).isOne();
        assertThat(sessions.isActive("phase05-session", user.getId())).isTrue();
        sessions.revoke("phase05-session", user.getId());
        assertThat(sessions.isActive("phase05-session", user.getId())).isFalse();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_sessions
                WHERE id = 'phase05-session' AND user_id = ? AND revoked_at IS NOT NULL
                """, Integer.class, user.getId())).isOne();
        assertThat(Integer.parseInt(flyway.info().current().getVersion().getVersion()))
                .isGreaterThanOrEqualTo(1402);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }

    @Test
    void fifthRejectedLoginCommitsLockHistoryAndOneSecurityEvent() {
        User user = new User();
        user.setUsername("locked-after-five");
        user.setPasswordHash(passwords.encode("Correct123"));
        user.setUserType(User.UserType.ADMIN);
        user.setStatus(User.UserStatus.ACTIVE);
        user.setFailedLoginCount(0);
        user = users.saveAndFlush(user);

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> auth.login(
                    new LoginRequest("locked-after-five", "Wrong123"),
                    "198.51.100.25", "Chrome/152"))
                    .isInstanceOf(BusinessException.class);
        }

        User persisted = users.findById(user.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(User.UserStatus.LOCKED);
        assertThat(persisted.getFailedLoginCount()).isEqualTo(5);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM login_history
                WHERE user_id = ? AND outcome = 'INVALID_CREDENTIALS'
                """, Integer.class, user.getId())).isEqualTo(5);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM security_events
                WHERE actor_user_id = ? AND event_type = 'REPEATED_LOGIN_FAILURE'
                  AND result_code = 'DETECTED'
                """, Integer.class, user.getId())).isOne();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = RedisAutoConfiguration.class)
    @EnableJpaRepositories(basePackageClasses = UserRepository.class)
    @EntityScan(basePackageClasses = User.class)
    @Import({AuthService.class, IdentitySessionService.class, LoginAnomalyService.class,
            SecurityEventService.class,
            Dependencies.class})
    static class IdentityVerificationApplication { }

    @TestConfiguration(proxyBeanMethods = false)
    static class Dependencies {
        @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(4); }
        @Bean JwtTokenProvider jwtTokenProvider() { return mock(JwtTokenProvider.class); }
    }
}
