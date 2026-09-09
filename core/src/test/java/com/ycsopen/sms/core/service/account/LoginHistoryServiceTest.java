package com.ycsopen.sms.core.service.account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class LoginHistoryServiceTest {

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:login-history;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP TABLE IF EXISTS login_history");
        jdbc.execute("""
                CREATE TABLE login_history (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT,
                    username VARCHAR(50) NOT NULL,
                    login_ip VARCHAR(45) NOT NULL,
                    user_agent VARCHAR(512),
                    outcome VARCHAR(32) NOT NULL,
                    occurred_at TIMESTAMP NOT NULL
                )
                """);
        jdbc.update("""
                INSERT INTO login_history(user_id, username, login_ip, user_agent, outcome, occurred_at)
                VALUES (7, 'admin', '10.0.0.1', 'browser-a', 'SUCCESS', TIMESTAMP '2026-09-07 08:00:00'),
                       (7, 'admin', '10.0.0.2', 'browser-b', 'INVALID_CREDENTIALS', TIMESTAMP '2026-09-07 09:00:00'),
                       (8, 'operator', '10.0.0.3', 'browser-c', 'SUCCESS', TIMESTAMP '2026-09-07 10:00:00')
                """);
    }

    @Test
    void queriesDatabaseByUserNewestFirstWithStablePagination() {
        var page = new LoginHistoryService(jdbc).query(7L, 0, 1);

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.username()).isEqualTo("admin");
            assertThat(item.loginIp()).isEqualTo("10.0.0.2");
            assertThat(item.outcome()).isEqualTo("INVALID_CREDENTIALS");
        });
    }
}
