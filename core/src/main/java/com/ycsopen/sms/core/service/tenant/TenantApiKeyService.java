package com.ycsopen.sms.core.service.tenant;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.service.audit.OperationAuditService;
import com.ycsopen.sms.core.web.dto.TenantApiKeyCreateRequest;
import com.ycsopen.sms.core.web.dto.TenantApiKeyResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

/** Tenant-owned HTTP API key lifecycle. Secret plaintext exists only in create memory. */
@Service
public class TenantApiKeyService {
    private static final Pattern IP = Pattern.compile("^[A-Za-z0-9:.\\-/]+$");
    private final JdbcTemplate jdbc;
    private final TenantCredentialSecretProtectionService protection;
    private final OperationAuditService audits;
    private final ApplicationEventPublisher events;
    private final SecureRandom random = new SecureRandom();

    public TenantApiKeyService(JdbcTemplate jdbc, TenantCredentialSecretProtectionService protection,
                               OperationAuditService audits, ApplicationEventPublisher events) {
        this.jdbc = jdbc; this.protection = protection; this.audits = audits; this.events = events;
    }

    @Transactional(readOnly = true)
    public List<TenantApiKeyResponse> list(long actorId) {
        long tenant = credentialTenant(actorId);
        return jdbc.query("""
                SELECT id,app_key,key_name,description,status,ip_whitelist,rate_limit_per_sec,
                       rate_limit_per_min,rate_limit_per_hour,rate_limit_per_day,expire_time,last_used_time
                  FROM tenant_api_keys WHERE tenant_id=? ORDER BY id
                """, (r,n) -> view(r.getLong("id"), r.getString("app_key"), r.getString("key_name"),
                r.getString("description"), r.getString("status"), r.getString("ip_whitelist"),
                r.getInt("rate_limit_per_sec"), r.getInt("rate_limit_per_min"),
                r.getInt("rate_limit_per_hour"), r.getInt("rate_limit_per_day"),
                r.getTimestamp("expire_time") == null ? null : r.getTimestamp("expire_time").toLocalDateTime(),
                r.getTimestamp("last_used_time") == null ? null : r.getTimestamp("last_used_time").toLocalDateTime()), tenant);
    }

    @Transactional
    public TenantApiKeyResponse create(long actorId, TenantApiKeyCreateRequest request) {
        long tenant = credentialTenant(actorId);
        validate(request);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM tenant_api_keys WHERE tenant_id=? AND key_name=?",
                Integer.class, tenant, request.name()) > 0) {
            throw new BusinessException("DUPLICATE_CREDENTIAL_NAME", "凭证名称已存在");
        }
        String appKey = uniqueKey();
        char[] secret = randomSecret();
        long id = positiveId();
        byte[] encrypted = protection.protect(tenant, id, "app_secret_encrypted", secret);
        try {
            jdbc.update("""
                    INSERT INTO tenant_api_keys(id,tenant_id,app_key,app_secret_encrypted,key_name,description,
                      status,ip_whitelist,rate_limit_per_sec,rate_limit_per_min,rate_limit_per_hour,rate_limit_per_day,expire_time)
                    VALUES (?,?,?,?,?,?,'ACTIVE',?,?,?,?,?,?)
                    """, id, tenant, appKey, encrypted, request.name(), request.description(), normalizeWhitelist(request.ipWhitelist()),
                    request.perSecond(), request.perMinute(), request.perHour(), request.perDay(), request.expireTime());
            audit(actorId, id, "CREATE");
            return new TenantApiKeyResponse(id, appKey, request.name(), request.description(), "ACTIVE",
                    request.ipWhitelist(), request.perSecond(), request.perMinute(), request.perHour(), request.perDay(),
                    request.expireTime(), null, new String(secret));
        } finally { java.util.Arrays.fill(encrypted, (byte) 0); java.util.Arrays.fill(secret, '\0'); }
    }

    @Transactional
    public void revoke(long actorId, long id) {
        long tenant = credentialTenant(actorId);
        int changed = jdbc.update("UPDATE tenant_api_keys SET status='DISABLED',revoked_at=CURRENT_TIMESTAMP "
                + "WHERE id=? AND tenant_id=? AND status='ACTIVE'", id, tenant);
        if (changed != 1) throw new BusinessException("CREDENTIAL_NOT_FOUND", "凭证不存在或已停用");
        audit(actorId, id, "REVOKE");
        publishAfterCommit(new TenantCredentialRevokedEvent(tenant, "HTTP_API_KEY", id, Instant.now()));
    }

    private long credentialTenant(long actor) {
        String type = jdbc.queryForObject("SELECT user_type FROM users WHERE id=?", String.class, actor);
        if (!"TENANT_ADMIN".equals(type) && !"TENANT_DEV".equals(type)) throw new BusinessException("FORBIDDEN", "无权执行此操作");
        Long tenant = jdbc.queryForObject("SELECT tenant_id FROM users WHERE id=?", Long.class, actor);
        if (tenant == null) throw new BusinessException("TENANT_REQUIRED", "当前账号不属于机构");
        return tenant;
    }
    private static void validate(TenantApiKeyCreateRequest r) {
        if (r == null || r.name() == null || r.name().isBlank() || r.name().length() > 64
                || r.perSecond() < 1 || r.perMinute() < r.perSecond() || r.perHour() < r.perMinute()
                || r.perDay() < r.perHour() || (r.ipWhitelist() != null && !r.ipWhitelist().isBlank()
                && !IP.matcher(r.ipWhitelist()).matches())) throw new BusinessException("INVALID_POLICY", "凭证策略不合法");
    }
    /** Stores MySQL JSON as a canonical array while accepting the documented comma-separated UI form. */
    static String normalizeWhitelist(String value) {
        if (value == null || value.isBlank()) return null;
        String[] entries = value.split(",");
        return "[\"" + java.util.Arrays.stream(entries).map(String::trim)
                .collect(java.util.stream.Collectors.joining("\",\"")) + "\"]";
    }
    private String uniqueKey() { byte[] value = new byte[24]; random.nextBytes(value); return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
    private char[] randomSecret() { byte[] value = new byte[32]; random.nextBytes(value); return Base64.getUrlEncoder().withoutPadding().encodeToString(value).toCharArray(); }
    /** Keep numeric IDs lossless when the browser sends them back as JSON numbers. */
    private long positiveId() { return random.nextLong(1, 9_000_000_000_000_000L); }
    private static TenantApiKeyResponse view(long id,String key,String name,String description,String status,String ips,int s,int m,int h,int d,LocalDateTime exp,LocalDateTime last) { return new TenantApiKeyResponse(id,key,name,description,status,ips,s,m,h,d,exp,last,"******"); }
    private void audit(long actor,long id,String op) { audits.append(new OperationAuditService.AuditCommand(actor,"TENANT_API_KEY_"+op,"TENANT_API_KEY",String.valueOf(id),"INTERNAL","/tenant/api/keys","{}","SUCCESS",200,"internal",null,0)); }
    private void publishAfterCommit(TenantCredentialRevokedEvent event) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            events.publishEvent(event);
            return;
        }
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() { events.publishEvent(event); }
                });
    }
}
