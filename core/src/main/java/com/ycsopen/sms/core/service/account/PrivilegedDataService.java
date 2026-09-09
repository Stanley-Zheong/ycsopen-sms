package com.ycsopen.sms.core.service.account;

import com.ycsopen.sms.core.service.audit.OperationAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Purpose-bound plaintext boundary for the allowlisted platform-account phone field. */
@Service
public class PrivilegedDataService {

    private final PlatformAccountPhoneStore phones;
    private final OperationAuditService audits;
    private final Clock clock;

    @Autowired
    public PrivilegedDataService(PlatformAccountPhoneStore phones, OperationAuditService audits) {
        this(phones, audits, Clock.systemUTC());
    }

    PrivilegedDataService(PlatformAccountPhoneStore phones, OperationAuditService audits, Clock clock) {
        this.phones = phones;
        this.audits = audits;
        this.clock = clock;
    }

    @Transactional
    public RevealedValue revealPhone(long targetUserId, long actorUserId, RevealPurpose purpose,
                                     String clientIp, String traceId) {
        if (purpose == null) {
            throw new IllegalArgumentException("reveal purpose is required");
        }
        String value = phones.revealForPrivilegedAccess(targetUserId);
        long auditId = audits.append(new OperationAuditService.AuditCommand(
                actorUserId, "SENSITIVE_REVEAL", "PLATFORM_ACCOUNT_PHONE",
                String.valueOf(targetUserId), "POST",
                "/api/v1/console/platform-accounts/{userId}/phone/reveal",
                "{\"purpose\":\"" + purpose.name() + "\"}", "SUCCESS", 200,
                clientIp, traceId, 0));
        return new RevealedValue(value, Instant.now(clock).plus(60, ChronoUnit.SECONDS), auditId);
    }

    public enum RevealPurpose { CUSTOMER_SUPPORT, SECURITY_INVESTIGATION, COMPLIANCE_REVIEW }

    public record RevealedValue(String value, Instant expiresAt, long auditId) { }
}
