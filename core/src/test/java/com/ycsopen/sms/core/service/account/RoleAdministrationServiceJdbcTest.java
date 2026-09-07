package com.ycsopen.sms.core.service.account;

import com.ycsopen.sms.core.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleAdministrationServiceJdbcTest {
    private JdbcTemplate jdbc;
    private RoleAdministrationService roles;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:role-admin-" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE roles (
                    id BIGINT PRIMARY KEY, role_code VARCHAR(50), role_name VARCHAR(100),
                    description VARCHAR(1000), role_type VARCHAR(20), status VARCHAR(20), tenant_id BIGINT)
                """);
        jdbc.execute("""
                CREATE TABLE permissions (
                    id BIGINT PRIMARY KEY, permission_code VARCHAR(100), permission_name VARCHAR(100),
                    resource_type VARCHAR(20), resource_path VARCHAR(200), http_method VARCHAR(10),
                    parent_id BIGINT, sort_order INT, status VARCHAR(20))
                """);
        jdbc.execute("""
                CREATE TABLE user_roles (
                    user_id BIGINT, role_id BIGINT, granted_by VARCHAR(50),
                    granted_at TIMESTAMP, expires_at TIMESTAMP, PRIMARY KEY(user_id, role_id))
                """);
        jdbc.execute("CREATE TABLE role_permissions (role_id BIGINT, permission_id BIGINT, PRIMARY KEY(role_id, permission_id))");
        jdbc.execute("""
                CREATE TABLE users (
                    id BIGINT PRIMARY KEY, username VARCHAR(50), user_type VARCHAR(20),
                    last_login_time TIMESTAMP, last_login_ip VARCHAR(45))
                """);
        jdbc.execute("""
                CREATE TABLE role_change_history (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY, role_id BIGINT, actor_user_id BIGINT,
                    action VARCHAR(32), before_state VARCHAR(1000), after_state VARCHAR(1000), occurred_at TIMESTAMP)
                """);
        jdbc.update("INSERT INTO roles VALUES (7, 'CUSTOM', '自定义角色', '', 'PLATFORM', 'ACTIVE', NULL)");
        jdbc.update("INSERT INTO roles VALUES (8, 'TARGET', '迁移角色', '', 'PLATFORM', 'ACTIVE', NULL)");
        for (int id = 1; id <= 4; id++) {
            String type = List.of("MENU", "BUTTON", "API", "DATA").get(id - 1);
            jdbc.update("INSERT INTO permissions VALUES (?, ?, ?, ?, NULL, NULL, NULL, ?, 'ACTIVE')",
                    id, "identity:" + type.toLowerCase(), type + "权限", type, id);
            jdbc.update("INSERT INTO role_permissions VALUES (7, ?)", id);
        }
        jdbc.update("INSERT INTO user_roles VALUES (5, 7, '1', CURRENT_TIMESTAMP, NULL)");
        jdbc.update("INSERT INTO users VALUES (5, 'admin', 'ADMIN', CURRENT_TIMESTAMP, '192.0.2.5')");
        jdbc.update("INSERT INTO users VALUES (99, 'root-admin', 'ADMIN', CURRENT_TIMESTAMP, '192.0.2.99')");
        roles = new RoleAdministrationService(jdbc);
    }

    @Test
    void resolvesAllFourGranularitiesFromCurrentDatabaseState() {
        assertThat(roles.currentPermissions(5L)).containsExactlyInAnyOrder(
                "identity:menu", "identity:button", "identity:api", "identity:data");

        jdbc.update("DELETE FROM role_permissions WHERE role_id = 7 AND permission_id = 4");

        assertThat(roles.currentPermissions(5L)).containsExactlyInAnyOrder(
                "identity:menu", "identity:button", "identity:api");
    }

    @Test
    void roleAssignmentAndDeletionRequireAValidMigrationTarget() {
        roles.replaceUserRoles(6L, List.of(7L), 99L);
        assertThat(roles.currentRoleIds(6L)).containsExactly(7L);

        assertThatThrownBy(() -> roles.deleteRole(7L, null, 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("迁移目标角色");

        roles.deleteRole(7L, 8L, 99L);
        assertThat(roles.currentRoleIds(5L)).containsExactly(8L);
        assertThat(roles.currentRoleIds(6L)).containsExactly(8L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM roles WHERE id = 7", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT actor_user_id FROM role_change_history WHERE role_id = 7", Long.class))
                .isEqualTo(99L);
        assertThat(jdbc.queryForObject("SELECT granted_by FROM user_roles WHERE user_id = 5 AND role_id = 8", String.class))
                .isEqualTo("99");
    }

    @Test
    void descriptionOnlyUpdateProducesDistinctCompleteAuditSnapshots() {
        roles.updatePlatformRole(7L, "CUSTOM", "自定义角色", "只读审计", "ACTIVE", 99L);

        var audit = jdbc.queryForMap("SELECT before_state, after_state FROM role_change_history WHERE role_id = 7");
        assertThat(audit.get("before_state")).asString().contains("description=");
        assertThat(audit.get("after_state")).asString().contains("description=只读审计");
        assertThat(audit.get("before_state")).isNotEqualTo(audit.get("after_state"));
    }

    @Test
    void accountOverviewReturnsCurrentRolesAndAllFourPermissionGranularities() {
        var overview = roles.accountOverview(5L);

        assertThat(overview.username()).isEqualTo("admin");
        assertThat(overview.userType()).isEqualTo("ADMIN");
        assertThat(overview.roleNames()).containsExactly("自定义角色");
        assertThat(overview.permissions())
                .extracting(RoleAdministrationService.PermissionGrant::resourceType)
                .containsExactly("API", "BUTTON", "DATA", "MENU");
        assertThat(overview.lastLoginAt()).isNotBlank();
        assertThat(overview.lastLoginIp()).isEqualTo("192.0.2.5");
    }

    @Test
    void delegatedActorCannotChangeOwnRoleOrGrantPermissionsTheyDoNotHold() {
        jdbc.update("UPDATE users SET user_type = 'OPERATOR' WHERE id = 5");
        jdbc.update("INSERT INTO roles VALUES (9, 'LIMITED', '受限角色', '', 'PLATFORM', 'ACTIVE', NULL)");
        jdbc.update("INSERT INTO users VALUES (6, 'operator', 'OPERATOR', NULL, NULL)");
        jdbc.update("INSERT INTO user_roles VALUES (6, 9, '99', CURRENT_TIMESTAMP, NULL)");
        jdbc.update("INSERT INTO role_permissions VALUES (9, 1)");

        assertThatThrownBy(() -> roles.replacePermissions(7L, List.of(1L), 5L))
                .isInstanceOfSatisfying(BusinessException.class, failure ->
                        assertThat(failure.getErrorCode()).isEqualTo("SELF_ROLE_MUTATION_FORBIDDEN"));

        assertThatThrownBy(() -> roles.replacePermissions(8L, List.of(1L, 2L), 6L))
                .isInstanceOfSatisfying(BusinessException.class, failure ->
                        assertThat(failure.getErrorCode()).isEqualTo("ROLE_GRANT_EXCEEDS_AUTHORITY"));
    }

    @Test
    void delegatedActorCannotAssignOrMigrateUsersIntoMorePrivilegedRoles() {
        jdbc.update("INSERT INTO roles VALUES (9, 'LIMITED', '受限角色', '', 'PLATFORM', 'ACTIVE', NULL)");
        jdbc.update("INSERT INTO users VALUES (6, 'operator', 'OPERATOR', NULL, NULL)");
        jdbc.update("INSERT INTO user_roles VALUES (6, 9, '99', CURRENT_TIMESTAMP, NULL)");
        jdbc.update("INSERT INTO role_permissions VALUES (9, 1)");

        assertThatThrownBy(() -> roles.replaceUserRoles(10L, List.of(7L), 6L))
                .isInstanceOfSatisfying(BusinessException.class, failure ->
                        assertThat(failure.getErrorCode()).isEqualTo("ROLE_GRANT_EXCEEDS_AUTHORITY"));

        assertThatThrownBy(() -> roles.deleteRole(8L, 7L, 6L))
                .isInstanceOfSatisfying(BusinessException.class, failure ->
                        assertThat(failure.getErrorCode()).isEqualTo("ROLE_GRANT_EXCEEDS_AUTHORITY"));
    }

    @Test
    void onlyAdministratorCanCreateOrPromoteAnAdministratorAccount() {
        jdbc.update("INSERT INTO users VALUES (6, 'operator', 'OPERATOR', NULL, NULL)");

        assertThatThrownBy(() -> roles.assertPlatformAccountTypeGrantAllowed("ADMIN", 6L))
                .isInstanceOfSatisfying(BusinessException.class, failure ->
                        assertThat(failure.getErrorCode()).isEqualTo("ADMIN_GRANT_FORBIDDEN"));

        roles.assertPlatformAccountTypeGrantAllowed("ADMIN", 99L);
        roles.assertPlatformAccountTypeGrantAllowed("OPERATOR", 6L);
    }
}
