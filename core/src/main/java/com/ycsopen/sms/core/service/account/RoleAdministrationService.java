package com.ycsopen.sms.core.service.account;

import com.ycsopen.sms.core.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Transactional administration of role membership and four-granularity permissions. */
@Service
public class RoleAdministrationService {
    private final JdbcTemplate jdbc;

    public RoleAdministrationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Set<String> currentPermissions(long userId) {
        return Set.copyOf(jdbc.queryForList("""
                SELECT p.permission_code FROM permissions p
                JOIN role_permissions rp ON rp.permission_id = p.id
                JOIN user_roles ur ON ur.role_id = rp.role_id
                JOIN roles r ON r.id = ur.role_id
                WHERE ur.user_id = ? AND p.status = 'ACTIVE' AND r.status = 'ACTIVE'
                  AND (ur.expires_at IS NULL OR ur.expires_at > CURRENT_TIMESTAMP)
                """, String.class, userId));
    }

    public List<PermissionGrant> currentPermissionScopes(long userId) {
        return jdbc.query("""
                SELECT p.permission_code, p.resource_type FROM permissions p
                JOIN role_permissions rp ON rp.permission_id = p.id
                JOIN user_roles ur ON ur.role_id = rp.role_id
                JOIN roles r ON r.id = ur.role_id
                WHERE ur.user_id = ? AND p.status = 'ACTIVE' AND r.status = 'ACTIVE'
                  AND (ur.expires_at IS NULL OR ur.expires_at > CURRENT_TIMESTAMP)
                ORDER BY p.resource_type, p.sort_order, p.id
                """, (result, row) -> new PermissionGrant(
                result.getString("permission_code"), result.getString("resource_type")), userId);
    }

    public List<Long> currentRoleIds(long userId) {
        return jdbc.queryForList("""
                SELECT r.id FROM roles r JOIN user_roles ur ON ur.role_id = r.id
                WHERE ur.user_id = ? AND r.role_type = 'PLATFORM'
                  AND (ur.expires_at IS NULL OR ur.expires_at > CURRENT_TIMESTAMP)
                ORDER BY r.id
                """, Long.class, userId);
    }

    @Transactional
    public void replaceUserRoles(long userId, Collection<Long> roleIds, long actorUserId) {
        List<Long> distinctIds = roleIds == null ? List.of() : roleIds.stream().distinct().toList();
        if (distinctIds.isEmpty()) {
            throw new BusinessException("PLATFORM_ROLE_REQUIRED", "平台账号必须至少分配一个有效角色");
        }
        Map<Long, String> lockedRoles = lockPlatformRoles(distinctIds);
        if (lockedRoles.size() != distinctIds.size()
                || lockedRoles.values().stream().anyMatch(status -> !"ACTIVE".equals(status))) {
            throw new BusinessException("INVALID_PLATFORM_ROLE", "角色不存在或已停用");
        }
        if (!isAdministrator(actorUserId)) {
            if (userId == actorUserId) {
                throw new BusinessException("SELF_ROLE_MUTATION_FORBIDDEN", "不能修改自己所属的角色");
            }
            assertRolePermissionsWithinActorAuthority(distinctIds, actorUserId);
        }
        jdbc.update("DELETE FROM user_roles WHERE user_id = ?", userId);
        distinctIds.forEach(roleId -> jdbc.update("""
                INSERT INTO user_roles(user_id, role_id, granted_by, granted_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """, userId, roleId, String.valueOf(actorUserId)));
    }

    public List<RoleSummary> listPlatformRoles() {
        return jdbc.query("""
                SELECT r.id, r.role_code, r.role_name, r.description, r.status,
                       (SELECT COUNT(*) FROM user_roles ur WHERE ur.role_id = r.id) AS user_count
                FROM roles r WHERE r.role_type = 'PLATFORM' ORDER BY r.id
                """, (result, row) -> new RoleSummary(
                result.getLong("id"),
                result.getString("role_code"),
                result.getString("role_name"),
                result.getString("description"),
                result.getString("status"),
                result.getLong("user_count"),
                permissionIds(result.getLong("id"))));
    }

    public List<PermissionSummary> listPermissions() {
        return jdbc.query("""
                SELECT id, permission_code, permission_name, resource_type, resource_path,
                       http_method, parent_id, sort_order, status
                FROM permissions ORDER BY resource_type, sort_order, id
                """, (result, row) -> new PermissionSummary(
                result.getLong("id"),
                result.getString("permission_code"),
                result.getString("permission_name"),
                result.getString("resource_type"),
                result.getString("resource_path"),
                result.getString("http_method"),
                result.getObject("parent_id", Long.class),
                result.getInt("sort_order"),
                result.getString("status")));
    }

    @Transactional
    public long createPlatformRole(String roleCode, String roleName, String description, long actorUserId) {
        validateRole(roleCode, roleName);
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO roles(role_code, role_name, description, role_type, status)
                    VALUES (?, ?, ?, 'PLATFORM', 'ACTIVE')
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, roleCode);
            statement.setString(2, roleName);
            statement.setString(3, description);
            return statement;
        }, key);
        if (key.getKey() == null) {
            throw new IllegalStateException("role identifier was not generated");
        }
        long roleId = key.getKey().longValue();
        audit(roleId, actorUserId, "CREATE", null,
                roleSnapshot(roleCode, roleName, description, "ACTIVE"));
        return roleId;
    }

    @Transactional
    public void updatePlatformRole(long roleId, String roleCode, String roleName,
                                   String description, String status, long actorUserId) {
        validateRole(roleCode, roleName);
        if (!("ACTIVE".equals(status) || "DISABLED".equals(status))) {
            throw new BusinessException("INVALID_ROLE_STATUS", "角色状态无效");
        }
        if (!lockPlatformRoles(List.of(roleId)).containsKey(roleId)) {
            throw new BusinessException("ROLE_NOT_FOUND", "角色不存在");
        }
        assertNotDelegatedActorOwnRole(roleId, actorUserId);
        String before = currentRoleSnapshot(roleId);
        int updated = jdbc.update("""
                UPDATE roles SET role_code = ?, role_name = ?, description = ?, status = ?
                WHERE id = ? AND role_type = 'PLATFORM'
                """, roleCode, roleName, description, status, roleId);
        if (updated != 1) {
            throw new BusinessException("ROLE_NOT_FOUND", "角色不存在");
        }
        audit(roleId, actorUserId, "UPDATE", before,
                roleSnapshot(roleCode, roleName, description, status));
    }

    @Transactional
    public void replacePermissions(long roleId, Collection<Long> permissionIds, long actorUserId) {
        if (!lockPlatformRoles(List.of(roleId)).containsKey(roleId)) {
            throw new BusinessException("ROLE_NOT_FOUND", "角色不存在");
        }
        boolean administrator = isAdministrator(actorUserId);
        if (!administrator && currentRoleIds(actorUserId).contains(roleId)) {
            throw new BusinessException("SELF_ROLE_MUTATION_FORBIDDEN", "不能修改自己所属角色的权限");
        }
        List<Long> distinctIds = permissionIds == null
                ? List.of()
                : permissionIds.stream().distinct().toList();
        if (!distinctIds.isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(distinctIds.size(), "?"));
            Integer existing = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM permissions WHERE status = 'ACTIVE' AND id IN (" + placeholders + ")",
                    Integer.class,
                    distinctIds.toArray());
            if (existing == null || existing != distinctIds.size()) {
                throw new BusinessException("INVALID_PERMISSION", "权限不存在或已停用");
            }
        }
        if (!administrator && !currentPermissionIds(actorUserId).containsAll(distinctIds)) {
            throw new BusinessException("ROLE_GRANT_EXCEEDS_AUTHORITY", "不能授予自己未持有的权限");
        }
        List<Long> before = permissionIds(roleId);
        jdbc.update("DELETE FROM role_permissions WHERE role_id = ?", roleId);
        distinctIds.forEach(permissionId -> jdbc.update(
                "INSERT INTO role_permissions(role_id, permission_id) VALUES (?, ?)", roleId, permissionId));
        audit(roleId, actorUserId, "PERMISSIONS", before.toString(), distinctIds.toString());
    }

    @Transactional
    public void deleteRole(long roleId, Long replacementRoleId, long actorUserId) {
        if (replacementRoleId != null && replacementRoleId == roleId) {
            throw new BusinessException("INVALID_ROLE_REPLACEMENT", "迁移目标角色不能是待删除角色");
        }
        List<Long> roleIds = replacementRoleId == null
                ? List.of(roleId)
                : java.util.stream.Stream.of(roleId, replacementRoleId).distinct().sorted().toList();
        Map<Long, String> lockedRoles = lockPlatformRoles(roleIds);
        if (!lockedRoles.containsKey(roleId)) {
            throw new BusinessException("ROLE_NOT_FOUND", "角色不存在");
        }
        if (replacementRoleId != null && !"ACTIVE".equals(lockedRoles.get(replacementRoleId))) {
            throw new BusinessException("INVALID_ROLE_REPLACEMENT", "迁移目标角色不存在或已停用");
        }
        assertNotDelegatedActorOwnRole(roleId, actorUserId);
        if (replacementRoleId != null && !isAdministrator(actorUserId)) {
            assertRolePermissionsWithinActorAuthority(List.of(replacementRoleId), actorUserId);
        }
        Integer users = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_roles WHERE role_id = ?", Integer.class, roleId);
        if (users != null && users > 0 && replacementRoleId == null) {
            throw new BusinessException("ROLE_IN_USE", "角色仍有关联用户，请先选择迁移目标角色");
        }
        String before = currentRoleSnapshot(roleId);
        if (users != null && users > 0) {
            jdbc.update("""
                    INSERT INTO user_roles(user_id, role_id, granted_by, granted_at)
                    SELECT source.user_id, ?, ?, CURRENT_TIMESTAMP
                    FROM user_roles source
                    LEFT JOIN user_roles existing
                      ON existing.user_id = source.user_id AND existing.role_id = ?
                    WHERE source.role_id = ? AND existing.user_id IS NULL
                    """, replacementRoleId, String.valueOf(actorUserId), replacementRoleId, roleId);
            jdbc.update("DELETE FROM user_roles WHERE role_id = ?", roleId);
        }
        jdbc.update("DELETE FROM role_permissions WHERE role_id = ?", roleId);
        jdbc.update("DELETE FROM roles WHERE id = ?", roleId);
        audit(roleId, actorUserId, "DELETE", before,
                "deleted;replacement=" + replacementRoleId + ";migratedUsers=" + (users == null ? 0 : users));
    }

    /** Prevents delegated account managers from manufacturing an administrator identity. */
    public void assertPlatformAccountTypeGrantAllowed(String requestedUserType, long actorUserId) {
        if ("ADMIN".equals(requestedUserType) && !isAdministrator(actorUserId)) {
            throw new BusinessException("ADMIN_GRANT_FORBIDDEN", "只有管理员可以创建或变更管理员账号");
        }
    }

    public AccountOverview accountOverview(long userId) {
        AccountIdentity identity = jdbc.queryForObject("""
                SELECT id, username, user_type, last_login_time, last_login_ip
                FROM users WHERE id = ?
                """, (result, row) -> new AccountIdentity(
                result.getLong("id"),
                result.getString("username"),
                result.getString("user_type"),
                result.getTimestamp("last_login_time") == null
                        ? null : result.getTimestamp("last_login_time").toLocalDateTime().toString(),
                result.getString("last_login_ip")), userId);
        List<String> roleNames = jdbc.queryForList("""
                SELECT r.role_name FROM roles r JOIN user_roles ur ON ur.role_id = r.id
                WHERE ur.user_id = ? AND r.status = 'ACTIVE'
                  AND (ur.expires_at IS NULL OR ur.expires_at > CURRENT_TIMESTAMP)
                ORDER BY r.role_name
                """, String.class, userId);
        return new AccountOverview(identity.id(), identity.username(), identity.userType(),
                roleNames, currentPermissionScopes(userId), identity.lastLoginAt(), identity.lastLoginIp());
    }

    private List<Long> permissionIds(long roleId) {
        return jdbc.queryForList(
                "SELECT permission_id FROM role_permissions WHERE role_id = ? ORDER BY permission_id",
                Long.class,
                roleId);
    }

    private String currentRoleSnapshot(long roleId) {
        String snapshot = jdbc.queryForObject("""
                SELECT CONCAT('code=', role_code, ';name=', role_name,
                              ';description=', COALESCE(description, ''), ';status=', status) FROM roles
                WHERE id = ? AND role_type = 'PLATFORM'
                """, String.class, roleId);
        if (snapshot == null) {
            throw new BusinessException("ROLE_NOT_FOUND", "角色不存在");
        }
        return snapshot;
    }

    private void audit(long roleId, long actorUserId, String action,
                       String beforeState, String afterState) {
        jdbc.update("""
                INSERT INTO role_change_history
                    (role_id, actor_user_id, action, before_state, after_state, occurred_at)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, roleId, actorUserId, action, beforeState, afterState);
    }

    private Map<Long, String> lockPlatformRoles(Collection<Long> roleIds) {
        List<Long> sorted = roleIds.stream().distinct().sorted().toList();
        if (sorted.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(sorted.size(), "?"));
        Map<Long, String> locked = new LinkedHashMap<>();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                        SELECT id, status FROM roles
                        WHERE role_type = 'PLATFORM' AND id IN (%s)
                        ORDER BY id FOR UPDATE
                        """.formatted(placeholders), sorted.toArray());
        rows.forEach(row -> locked.put(((Number) row.get("id")).longValue(), String.valueOf(row.get("status"))));
        return locked;
    }

    private void assertNotDelegatedActorOwnRole(long roleId, long actorUserId) {
        if (!isAdministrator(actorUserId) && currentRoleIds(actorUserId).contains(roleId)) {
            throw new BusinessException("SELF_ROLE_MUTATION_FORBIDDEN", "不能修改自己所属的角色");
        }
    }

    private void assertRolePermissionsWithinActorAuthority(Collection<Long> roleIds, long actorUserId) {
        if (!currentPermissionIds(actorUserId).containsAll(activePermissionIdsForRoles(roleIds))) {
            throw new BusinessException("ROLE_GRANT_EXCEEDS_AUTHORITY", "不能分配权限高于自己的角色");
        }
    }

    private boolean isAdministrator(long userId) {
        return jdbc.queryForList(
                        "SELECT user_type FROM users WHERE id = ?", String.class, userId)
                .stream()
                .anyMatch("ADMIN"::equals);
    }

    private Set<Long> currentPermissionIds(long userId) {
        return Set.copyOf(jdbc.queryForList("""
                SELECT DISTINCT p.id FROM permissions p
                JOIN role_permissions rp ON rp.permission_id = p.id
                JOIN user_roles ur ON ur.role_id = rp.role_id
                JOIN roles r ON r.id = ur.role_id
                WHERE ur.user_id = ? AND p.status = 'ACTIVE' AND r.status = 'ACTIVE'
                  AND (ur.expires_at IS NULL OR ur.expires_at > CURRENT_TIMESTAMP)
                """, Long.class, userId));
    }

    private Set<Long> activePermissionIdsForRoles(Collection<Long> roleIds) {
        List<Long> sorted = roleIds.stream().distinct().sorted().toList();
        if (sorted.isEmpty()) {
            return Set.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(sorted.size(), "?"));
        return Set.copyOf(jdbc.queryForList("""
                SELECT DISTINCT p.id FROM permissions p
                JOIN role_permissions rp ON rp.permission_id = p.id
                JOIN roles r ON r.id = rp.role_id
                WHERE rp.role_id IN (%s) AND p.status = 'ACTIVE' AND r.status = 'ACTIVE'
                """.formatted(placeholders), Long.class, sorted.toArray()));
    }

    private static String roleSnapshot(String code, String name, String description, String status) {
        return "code=" + code + ";name=" + name + ";description="
                + (description == null ? "" : description) + ";status=" + status;
    }

    private static void validateRole(String roleCode, String roleName) {
        if (roleCode == null || !roleCode.matches("[A-Z][A-Z0-9_]{2,49}")) {
            throw new BusinessException("INVALID_ROLE_CODE", "角色编码须为 3-50 位大写字母、数字或下划线");
        }
        if (roleName == null || roleName.isBlank() || roleName.length() > 100) {
            throw new BusinessException("INVALID_ROLE_NAME", "角色名称不能为空且不能超过 100 个字符");
        }
    }

    public record RoleSummary(long id, String code, String name, String description,
                              String status, long userCount, List<Long> permissionIds) { }

    public record PermissionSummary(long id, String code, String name,
                                    String resourceType, String resourcePath, String httpMethod,
                                    Long parentId, int sortOrder, String status) { }

    public record AccountOverview(long id, String username, String userType, List<String> roleNames,
                                  List<PermissionGrant> permissions, String lastLoginAt, String lastLoginIp) { }

    public record PermissionGrant(String code, String resourceType) { }

    private record AccountIdentity(long id, String username, String userType,
                                   String lastLoginAt, String lastLoginIp) { }
}
