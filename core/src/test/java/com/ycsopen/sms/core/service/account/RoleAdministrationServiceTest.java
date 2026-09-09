package com.ycsopen.sms.core.service.account;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleAdministrationServiceTest {
    @Mock JdbcTemplate jdbc;

    @Test
    void blocksDeletionOfInUseRoleWithoutMigrationTarget() {
        when(jdbc.queryForList(argThat(sql -> sql != null && sql.contains("FOR UPDATE")), eq(7L)))
                .thenReturn(java.util.List.of(java.util.Map.of("id", 7L, "status", "ACTIVE")));
        when(jdbc.queryForObject(argThat(sql -> sql != null && sql.contains("FROM user_roles")), eq(Integer.class), eq(7L)))
                .thenReturn(2);
        assertThatThrownBy(() -> new RoleAdministrationService(jdbc).deleteRole(7L, null, 99L))
                .hasMessageContaining("角色仍有关联用户");
    }

    @Test
    void migratesUsersBeforeDeletingRole() {
        when(jdbc.queryForList(argThat(sql -> sql != null && sql.contains("FOR UPDATE")), eq(7L), eq(8L)))
                .thenReturn(java.util.List.of(
                        java.util.Map.of("id", 7L, "status", "ACTIVE"),
                        java.util.Map.of("id", 8L, "status", "ACTIVE")));
        when(jdbc.queryForObject(argThat(sql -> sql != null && sql.contains("FROM user_roles")), eq(Integer.class), eq(7L)))
                .thenReturn(2);
        when(jdbc.queryForObject(argThat(sql -> sql != null && sql.contains("SELECT CONCAT")), eq(String.class), eq(7L)))
                .thenReturn("code=CUSTOM;name=自定义角色;description=;status=ACTIVE");
        new RoleAdministrationService(jdbc).deleteRole(7L, 8L, 99L);

        InOrder order = inOrder(jdbc);
        order.verify(jdbc).queryForList(argThat(sql -> sql != null && sql.contains("FOR UPDATE")), eq(7L), eq(8L));
        order.verify(jdbc).queryForObject(argThat(sql -> sql != null && sql.contains("FROM user_roles")), eq(Integer.class), eq(7L));
        order.verify(jdbc).queryForObject(argThat(sql -> sql != null && sql.contains("SELECT CONCAT")), eq(String.class), eq(7L));
        order.verify(jdbc).update(argThat(sql -> sql != null && sql.contains("INSERT INTO user_roles")), eq(8L), eq("99"), eq(8L), eq(7L));
        order.verify(jdbc).update(argThat(sql -> sql != null && sql.contains("DELETE FROM user_roles")), eq(7L));
        order.verify(jdbc).update(argThat(sql -> sql != null && sql.contains("DELETE FROM role_permissions")), eq(7L));
        order.verify(jdbc).update(argThat(sql -> sql != null && sql.contains("DELETE FROM roles")), eq(7L));
        order.verify(jdbc).update(argThat(sql -> sql != null && sql.contains("INSERT INTO role_change_history")),
                eq(7L), eq(99L), eq("DELETE"), anyString(), anyString());
    }

    @Test
    void resolvesCurrentPermissionsOnEveryCall() {
        when(jdbc.queryForList(anyString(), eq(String.class), eq(5L)))
                .thenReturn(java.util.List.of("identity:menu"))
                .thenReturn(java.util.List.of("identity:roles:read"));
        RoleAdministrationService service = new RoleAdministrationService(jdbc);

        org.assertj.core.api.Assertions.assertThat(service.currentPermissions(5L))
                .containsExactly("identity:menu");
        org.assertj.core.api.Assertions.assertThat(service.currentPermissions(5L))
                .containsExactly("identity:roles:read");
        verify(jdbc, org.mockito.Mockito.times(2)).queryForList(anyString(), eq(String.class), eq(5L));
    }
}
