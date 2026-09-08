package com.ycsopen.sms.core.service.tenant;

import com.ycsopen.sms.core.common.security.object.ObjectAccessAuthorizationPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Objects;

/** Rechecks evidence ownership and live reviewer permission when a one-time capability is consumed. */
@Component
public final class QualificationObjectAuthorization implements ObjectAccessAuthorizationPort {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public QualificationObjectAuthorization(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    QualificationObjectAuthorization(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public boolean authorize(Request request) {
        if (request.capabilityState() != CapabilityState.ACTIVE
                || !clock.instant().isBefore(request.expiresAt())
                || !request.tenant().startsWith("tenant:")) return false;
        String draft = request.tenant().substring("tenant:".length());
        if (!ownsEvidence(request.protectedObjectId(), draft)) return false;
        if ("qualification-ocr".equals(request.purpose())) {
            return QualificationEvidenceService.OCR_SUBJECT.equals(request.subject());
        }
        if (!"qualification-review".equals(request.purpose())
                || !request.subject().matches("[0-9]{1,19}")) return false;
        try {
            return Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT COUNT(*) > 0
                      FROM users u
                     WHERE u.id = ? AND u.status = 'ACTIVE'
                       AND (u.user_type = 'ADMIN' OR EXISTS (
                            SELECT 1 FROM user_roles ur
                            JOIN roles r ON r.id = ur.role_id AND r.status = 'ACTIVE'
                            JOIN role_permissions rp ON rp.role_id = r.id
                            JOIN permissions p ON p.id = rp.permission_id AND p.status = 'ACTIVE'
                           WHERE ur.user_id = u.id
                             AND (ur.expires_at IS NULL OR ur.expires_at > CURRENT_TIMESTAMP)
                             AND p.permission_code = 'tenant:evidence:read'))
                    """, Boolean.class, Long.parseLong(request.subject())));
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private boolean ownsEvidence(String objectId, String draft) {
        try {
            return Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT COUNT(*) > 0
                      FROM ycs_crypto_protected_objects p
                      JOIN tenants t ON p.protected_object_id IN
                           (t.business_license_url, t.legal_rep_id_front_url, t.legal_rep_id_back_url,
                            t.shortlink_domain_proof_url, t.trademark_proof_url)
                     WHERE p.protected_object_id = ? AND p.tenant_draft_id = ?
                       AND p.object_state = 'CLAIMED'
                    """, Boolean.class, objectId, draft));
        } catch (RuntimeException unavailable) {
            return false;
        }
    }
}
