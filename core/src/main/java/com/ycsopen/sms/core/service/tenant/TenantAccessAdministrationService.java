package com.ycsopen.sms.core.service.tenant;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.service.audit.OperationAuditService;
import com.ycsopen.sms.core.web.dto.TenantAdministratorCreateRequest;
import com.ycsopen.sms.core.web.dto.TenantAdministratorResponse;
import com.ycsopen.sms.core.web.dto.TenantAdministratorUpdateRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Tenant-owned subaccount administration. The actor's tenant is always read from users. */
@Service
public class TenantAccessAdministrationService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final OperationAuditService audits;

    public TenantAccessAdministrationService(JdbcTemplate jdbc, PasswordEncoder passwords,
                                             OperationAuditService audits) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.audits = audits;
    }

    @Transactional(readOnly = true)
    public List<TenantAdministratorResponse> list(long actorId) {
        long tenantId = actorTenant(actorId);
        return jdbc.query("""
                SELECT u.id, u.username, u.real_name, u.user_type, u.status, u.created_at,
                       GROUP_CONCAT(r.role_name ORDER BY r.role_name SEPARATOR ', ') AS role_names
                  FROM users u
                  LEFT JOIN user_roles ur ON ur.user_id = u.id
                  LEFT JOIN roles r ON r.id = ur.role_id AND r.role_type = 'TENANT'
                 WHERE u.tenant_id = ? AND u.user_type IN ('TENANT_USER','TENANT_DEV')
                 GROUP BY u.id, u.username, u.real_name, u.user_type, u.status, u.created_at
                 ORDER BY u.id
                """, (row, n) -> new TenantAdministratorResponse(row.getLong("id"),
                        row.getString("username"), row.getString("real_name"),
                        row.getString("user_type"), row.getString("status"),
                        row.getTimestamp("created_at").toInstant(), row.getString("role_names")), tenantId);
    }

    @Transactional
    public TenantAdministratorResponse create(long actorId, TenantAdministratorCreateRequest request) {
        long tenantId = requireTenantAdmin(actorId);
        validate(request.username(), request.password(), request.userType());
        if (jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username = ?", Integer.class,
                request.username()) > 0) {
            throw new BusinessException("USERNAME_EXISTS", "用户名已存在");
        }
        long roleId = roleForTenant(tenantId, request.userType());
        jdbc.update("""
                INSERT INTO users(username,password_hash,real_name,user_type,tenant_id,status,created_by)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?)
                """, request.username(), passwords.encode(request.password()), request.realName(),
                request.userType(), tenantId, String.valueOf(actorId));
        long userId = jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class,
                request.username());
        jdbc.update("INSERT INTO user_roles(user_id,role_id,granted_by) VALUES (?,?,?)",
                userId, roleId, String.valueOf(actorId));
        audit(actorId, userId, "CREATE");
        return list(actorId).stream().filter(value -> value.id() == userId).findFirst().orElseThrow();
    }

    @Transactional
    public TenantAdministratorResponse update(long actorId, long userId,
                                              TenantAdministratorUpdateRequest request) {
        long tenantId = requireTenantAdmin(actorId);
        if (userId == actorId) throw new BusinessException("SELF_ACCOUNT_EDIT_FORBIDDEN", "不能修改自己的账号");
        validate(request.realName(), null, request.userType());
        int changed = jdbc.update("""
                UPDATE users SET real_name = ?, user_type = ?, status = ?
                 WHERE id = ? AND tenant_id = ? AND user_type IN ('TENANT_USER','TENANT_DEV')
                """, request.realName(), request.userType(), request.status(), userId, tenantId);
        if (changed != 1) throw new BusinessException("USER_NOT_FOUND", "账号不存在");
        long roleId = roleForTenant(tenantId, request.userType());
        jdbc.update("DELETE FROM user_roles WHERE user_id = ? AND role_id IN "
                + "(SELECT id FROM roles WHERE role_type='TENANT' AND tenant_id=?)", userId, tenantId);
        jdbc.update("INSERT INTO user_roles(user_id,role_id,granted_by) VALUES (?,?,?)",
                userId, roleId, String.valueOf(actorId));
        audit(actorId, userId, "UPDATE");
        return list(actorId).stream().filter(value -> value.id() == userId).findFirst().orElseThrow();
    }

    private long actorTenant(long actorId) {
        Long tenant = jdbc.queryForObject("SELECT tenant_id FROM users WHERE id = ?", Long.class, actorId);
        if (tenant == null || tenant < 1) throw new BusinessException("TENANT_REQUIRED", "当前账号不属于机构");
        return tenant;
    }

    private long requireTenantAdmin(long actorId) {
        String type = jdbc.queryForObject("SELECT user_type FROM users WHERE id = ?", String.class, actorId);
        if (!"TENANT_ADMIN".equals(type)) throw new BusinessException("FORBIDDEN", "无权执行此操作");
        return actorTenant(actorId);
    }

    private long roleForTenant(long tenantId, String type) {
        Long id = jdbc.query("""
                SELECT id FROM roles WHERE tenant_id = ? AND role_type = 'TENANT' AND status = 'ACTIVE'
                  AND role_code IN (?, ?) ORDER BY id LIMIT 1
                """, (row, n) -> row.getLong("id"), tenantId,
                "TENANT_" + type.substring("TENANT_".length()), type).stream().findFirst().orElse(null);
        if (id == null) throw new BusinessException("INVALID_TENANT_ROLE", "机构角色不存在或已停用");
        return id;
    }

    private static void validate(String name, String password, String type) {
        if (name == null || name.isBlank() || name.length() > 50 ||
                (!"TENANT_USER".equals(type) && !"TENANT_DEV".equals(type)))
            throw new BusinessException("INVALID_ACCOUNT", "机构账号字段不合法");
        if (password != null && password.length() < 8) throw new BusinessException("INVALID_PASSWORD", "密码长度不足");
    }

    private void audit(long actor, long resource, String action) {
        audits.append(new OperationAuditService.AuditCommand(actor, "TENANT_ACCOUNT_" + action,
                "TENANT_ACCOUNT", String.valueOf(resource), "INTERNAL", "/tenant/administrators",
                "{}", "SUCCESS", 200, "internal", null, 0));
    }
}
