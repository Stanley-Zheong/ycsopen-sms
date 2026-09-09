package com.ycsopen.sms.core.service.tenant;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.service.audit.OperationAuditService;
import com.ycsopen.sms.core.web.dto.TenantProtocolCredentialCreateRequest;
import com.ycsopen.sms.core.web.dto.TenantProtocolCredentialResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.InetAddress;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/** CMPP credential metadata only. It deliberately does not open a protocol session. */
@Service
public class TenantProtocolCredentialService {
    private final JdbcTemplate jdbc; private final TenantCredentialSecretProtectionService protection;
    private final OperationAuditService audits; private final ApplicationEventPublisher events;
    private final SecureRandom random = new SecureRandom();
    public TenantProtocolCredentialService(JdbcTemplate jdbc, TenantCredentialSecretProtectionService protection,
                                            OperationAuditService audits, ApplicationEventPublisher events) {
        this.jdbc=jdbc; this.protection=protection; this.audits=audits; this.events=events;
    }
    @Transactional(readOnly=true)
    public List<TenantProtocolCredentialResponse> list(long actor) {
        long tenant=tenant(actor);
        return jdbc.query("SELECT id,protocol,account_encrypted,spid,endpoint_host,endpoint_port,max_connections,tps_limit,window_size,ip_whitelist,status FROM tenant_protocol_credentials WHERE tenant_id=? ORDER BY created_at DESC, id DESC",
                (r,n)->new TenantProtocolCredentialResponse(r.getLong("id"),r.getString("protocol"),"******",r.getString("spid"),r.getString("endpoint_host"),r.getInt("endpoint_port"),r.getInt("max_connections"),r.getInt("tps_limit"),r.getInt("window_size"),r.getString("ip_whitelist"),r.getString("status"),"******"),tenant);
    }
    @Transactional
    public TenantProtocolCredentialResponse create(long actor,TenantProtocolCredentialCreateRequest req) {
        long tenant=tenant(actor); validate(req); long id=positiveId();
        char[] account=req.account().toCharArray(), password=req.password().toCharArray();
        byte[] ea=protection.protect(tenant,id,"account_encrypted",account);
        byte[] ep=protection.protect(tenant,id,"password_encrypted",password);
        try {
            jdbc.update("INSERT INTO tenant_protocol_credentials(id,tenant_id,protocol,account_encrypted,password_encrypted,spid,endpoint_host,endpoint_port,ip_whitelist,max_connections,tps_limit,window_size,status) VALUES (?,?,'CMPP',?,?,?,?,?,?,?,?,?,'ACTIVE')",
                    id,tenant,ea,ep,req.spid(),req.endpointHost(),req.endpointPort(),TenantApiKeyService.normalizeWhitelist(req.ipWhitelist()),req.maxConnections(),req.tpsLimit(),req.windowSize());
            audit(actor,id,"CREATE");
            return new TenantProtocolCredentialResponse(id,"CMPP","******",req.spid(),req.endpointHost(),req.endpointPort(),req.maxConnections(),req.tpsLimit(),req.windowSize(),req.ipWhitelist(),"ACTIVE",req.password());
        } finally { Arrays.fill(ea,(byte)0); Arrays.fill(ep,(byte)0); }
    }
    @Transactional
    public void revoke(long actor,long id) {
        long tenant=tenant(actor); int changed=jdbc.update("UPDATE tenant_protocol_credentials SET status='DISABLED',revoked_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status='ACTIVE'",id,tenant);
        if(changed!=1) throw new BusinessException("CREDENTIAL_NOT_FOUND","凭证不存在或已停用"); audit(actor,id,"REVOKE");
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            events.publishEvent(new TenantCredentialRevokedEvent(tenant,"CMPP",id,Instant.now()));
            return;
        }
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() { events.publishEvent(new TenantCredentialRevokedEvent(tenant,"CMPP",id,Instant.now())); }
                });
    }
    private long tenant(long actor) { String type=jdbc.queryForObject("SELECT user_type FROM users WHERE id=?",String.class,actor); if(!"TENANT_ADMIN".equals(type)&&!"TENANT_DEV".equals(type)) throw new BusinessException("FORBIDDEN","无权执行此操作"); Long t=jdbc.queryForObject("SELECT tenant_id FROM users WHERE id=?",Long.class,actor); if(t==null)throw new BusinessException("TENANT_REQUIRED","当前账号不属于机构"); return t; }
    private static void validate(TenantProtocolCredentialCreateRequest r) { if(r==null||r.endpointHost().isBlank()||r.endpointHost().length()>255||r.endpointPort()<1||r.endpointPort()>65535||r.maxConnections()<1||r.tpsLimit()<1||r.windowSize()<1||r.account().isBlank()||r.password().length()<8) throw new BusinessException("INVALID_POLICY","CMPP策略不合法"); try { if(!r.endpointHost().matches("^[A-Za-z0-9.-]+$")) InetAddress.getByName(r.endpointHost()); } catch(Exception e) { throw new BusinessException("INVALID_ENDPOINT","接入地址不合法"); } }
    /** Keep numeric IDs lossless when the browser sends them back as JSON numbers. */
    private long positiveId(){return random.nextLong(1,9_000_000_000_000_000L);}
    private void audit(long a,long id,String op){audits.append(new OperationAuditService.AuditCommand(a,"TENANT_CMPP_"+op,"TENANT_PROTOCOL_CREDENTIAL",String.valueOf(id),"INTERNAL","/tenant/cmpp/access","{}","SUCCESS",200,"internal",null,0));}
}
