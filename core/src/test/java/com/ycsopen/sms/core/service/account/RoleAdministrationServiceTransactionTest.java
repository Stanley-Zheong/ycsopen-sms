package com.ycsopen.sms.core.service.account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JdbcTest(properties = "spring.flyway.enabled=false")
@Import(RoleAdministrationService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RoleAdministrationServiceTransactionTest {
    @Autowired RoleAdministrationService roles;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach
    void setUp() {
        jdbc.execute("DROP TABLE IF EXISTS role_change_history");
        jdbc.execute("DROP TABLE IF EXISTS role_permissions");
        jdbc.execute("DROP TABLE IF EXISTS user_roles");
        jdbc.execute("DROP TABLE IF EXISTS roles");
        jdbc.execute("DROP TABLE IF EXISTS users");
        jdbc.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, user_type VARCHAR(20))");
        jdbc.execute("CREATE TABLE roles (id BIGINT PRIMARY KEY, role_code VARCHAR(50), role_name VARCHAR(100), description VARCHAR(1000), role_type VARCHAR(20), status VARCHAR(20))");
        jdbc.execute("CREATE TABLE user_roles (user_id BIGINT, role_id BIGINT, granted_by VARCHAR(50), granted_at TIMESTAMP, expires_at TIMESTAMP, PRIMARY KEY(user_id, role_id))");
        jdbc.execute("CREATE TABLE role_permissions (role_id BIGINT, permission_id BIGINT, PRIMARY KEY(role_id, permission_id))");
        jdbc.execute("""
                CREATE TABLE role_change_history (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY, role_id BIGINT, actor_user_id BIGINT,
                    action VARCHAR(32), before_state VARCHAR(1000),
                    after_state VARCHAR(1000), occurred_at TIMESTAMP)
                """);
        jdbc.update("INSERT INTO roles VALUES (7, 'SOURCE', '来源角色', '', 'PLATFORM', 'ACTIVE')");
        jdbc.update("INSERT INTO roles VALUES (8, 'TARGET', '目标角色', '', 'PLATFORM', 'ACTIVE')");
        jdbc.update("INSERT INTO user_roles VALUES (5, 7, '1', CURRENT_TIMESTAMP, NULL)");
        jdbc.update("INSERT INTO users VALUES (99, 'ADMIN')");
    }

    @Test
    void auditFailureRollsBackUserMigrationAndRoleDeletion() {
        jdbc.execute("ALTER TABLE role_change_history ADD CONSTRAINT reject_delete_audit CHECK (action <> 'DELETE')");

        assertThatThrownBy(() -> roles.deleteRole(7L, 8L, 99L))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM roles WHERE id = 7", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_roles WHERE user_id = 5 AND role_id = 7", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_roles WHERE user_id = 5 AND role_id = 8", Integer.class)).isZero();
    }

    @Test
    void deletionSerializesWithAnAssignmentThatAlreadyLockedTheSourceRole() throws Exception {
        var assignmentMayCommit = new CountDownLatch(1);
        var sourceRoleLocked = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var assignment = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                jdbc.queryForList("SELECT id FROM roles WHERE id = 7 FOR UPDATE", Long.class);
                sourceRoleLocked.countDown();
                await(assignmentMayCommit);
                jdbc.update("INSERT INTO user_roles VALUES (6, 7, '2', CURRENT_TIMESTAMP, NULL)");
            }));
            assertThat(sourceRoleLocked.await(5, TimeUnit.SECONDS)).isTrue();

            var deletion = executor.submit(() -> roles.deleteRole(7L, 8L, 99L));
            Thread.sleep(200);
            assignmentMayCommit.countDown();

            assignment.get(5, TimeUnit.SECONDS);
            deletion.get(5, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM user_roles WHERE user_id = 6 AND role_id = 7", Integer.class)).isZero();
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM user_roles WHERE user_id = 6 AND role_id = 8", Integer.class)).isOne();
        } finally {
            assignmentMayCommit.countDown();
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("concurrent role operation did not reach its release point");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }
}
