package com.ycsopen.sms.core.service.account;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.common.security.JwtTokenProvider;
import com.ycsopen.sms.core.domain.entity.User;
import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.service.configuration.PlatformConfigurationRegistry;
import com.ycsopen.sms.core.service.configuration.PlatformConfigurationRuntime;
import com.ycsopen.sms.core.web.dto.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({AuthService.class, IdentitySessionService.class, AuthServiceTransactionTest.Dependencies.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuthServiceTransactionTest {

    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;

    @BeforeEach
    void setUp() {
        jdbc.execute("DROP TABLE IF EXISTS login_history");
        jdbc.execute("""
                CREATE TABLE login_history (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT, username VARCHAR(50), login_ip VARCHAR(64),
                    user_agent VARCHAR(512), outcome VARCHAR(32), occurred_at TIMESTAMP)
                """);
        users.deleteAll();
        User user = new User();
        user.setUsername("admin");
        user.setPasswordHash(passwords.encode("Correct123"));
        user.setUserType(User.UserType.ADMIN);
        user.setStatus(User.UserStatus.ACTIVE);
        user.setFailedLoginCount(0);
        users.saveAndFlush(user);
    }

    @Test
    void fifthRejectedLoginCommitsLockAndEveryHistoryRow() {
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> auth.login(
                    new LoginRequest("admin", "Wrong123"), "127.0.0.1", "Chrome/152"))
                    .isInstanceOf(BusinessException.class);
        }

        User persisted = users.findByUsername("admin").orElseThrow();
        assertThat(persisted.getFailedLoginCount()).isEqualTo(5);
        assertThat(persisted.getStatus()).isEqualTo(User.UserStatus.LOCKED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM login_history", Integer.class)).isEqualTo(5);
    }

    @Test
    void concurrentRejectedLoginsCannotLoseFailureIncrements() throws Exception {
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(5)) {
            var futures = java.util.stream.IntStream.range(0, 5).mapToObj(ignored -> executor.submit(() -> {
                start.await();
                try {
                    auth.login(new LoginRequest("admin", "Wrong123"), "127.0.0.1", "Chrome/152");
                } catch (BusinessException expected) {
                    // Rejection is the expected result; persisted state is asserted below.
                }
                return null;
            })).toList();
            start.countDown();
            for (var future : futures) future.get();
        }

        User persisted = users.findByUsername("admin").orElseThrow();
        assertThat(persisted.getFailedLoginCount()).isEqualTo(5);
        assertThat(persisted.getStatus()).isEqualTo(User.UserStatus.LOCKED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM login_history", Integer.class)).isEqualTo(5);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Dependencies {
        @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
        @Bean JwtTokenProvider jwtTokenProvider() { return mock(JwtTokenProvider.class); }
        @Bean LoginAnomalyService loginAnomalyService() { return mock(LoginAnomalyService.class); }
        @Bean PlatformConfigurationRuntime platformConfigurationRuntime() {
            return new PlatformConfigurationRuntime(new PlatformConfigurationRegistry());
        }
    }
}
