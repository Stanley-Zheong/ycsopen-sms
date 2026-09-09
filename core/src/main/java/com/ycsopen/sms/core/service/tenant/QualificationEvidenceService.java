package com.ycsopen.sms.core.service.tenant;

import com.ycsopen.sms.core.common.security.object.ObjectCapabilityService;
import com.ycsopen.sms.core.common.security.object.PrivateObjectStorePort;
import com.ycsopen.sms.core.common.security.object.ProtectedObjectService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/** Application-mediated evidence read: browser callers never receive object or capability identity. */
@Service
@ConditionalOnProperty(prefix = "ycsopen.object-store", name = "enabled", havingValue = "true")
public class QualificationEvidenceService {
    static final String OCR_SUBJECT = "qualification-ocr";
    private final JdbcTemplate jdbc;
    private final ObjectCapabilityService capabilities;
    private final ProtectedObjectService objects;
    private final Clock clock;

    @Autowired
    public QualificationEvidenceService(JdbcTemplate jdbc, ObjectCapabilityService capabilities,
                                        ProtectedObjectService objects) {
        this(jdbc, capabilities, objects, Clock.systemUTC());
    }

    QualificationEvidenceService(JdbcTemplate jdbc, ObjectCapabilityService capabilities,
                                 ProtectedObjectService objects, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.capabilities = Objects.requireNonNull(capabilities);
        this.objects = Objects.requireNonNull(objects);
        this.clock = Objects.requireNonNull(clock);
    }

    public EvidenceContent readForReviewer(long tenantId, EvidenceKind kind, String reviewerSubject) {
        if (reviewerSubject == null || !reviewerSubject.matches("[0-9]{1,19}")) throw Failure.denied();
        return read(tenantId, kind, reviewerSubject, "qualification-review");
    }

    EvidenceContent readForInspection(long tenantId) {
        return read(tenantId, EvidenceKind.BUSINESS_LICENSE, OCR_SUBJECT, "qualification-ocr");
    }

    private EvidenceContent read(long tenantId, EvidenceKind kind, String subject, String accessPurpose) {
        EvidenceReference reference = reference(tenantId, kind);
        String tenantScope = "tenant:" + reference.tenantDraftId();
        try {
            String capabilityPath = capabilities.issue(new ObjectCapabilityService.IssueRequest(
                    reference.objectId(), tenantScope, subject, accessPurpose,
                    clock.instant().plus(30, ChronoUnit.SECONDS))).claimApplicationRelativePath();
            String token = capabilityPath.substring(capabilityPath.lastIndexOf('/') + 1);
            ProtectedObjectService.ProtectedObjectData data = objects.read(new ProtectedObjectService.ReadRequest(
                    reference.objectId(), token, tenantScope, subject, accessPurpose, kind.objectPurpose));
            return new EvidenceContent(data.mediaType(), data.bytes());
        } catch (ProtectedObjectService.Failure failure) {
            if (failure.category()
                    == ProtectedObjectService.Failure.Category.PROTECTED_OBJECT_ACCESS_DENIED) {
                throw Failure.denied();
            }
            throw Failure.unavailable();
        } catch (RuntimeException failure) {
            throw Failure.unavailable();
        }
    }

    private EvidenceReference reference(long tenantId, EvidenceKind kind) {
        List<EvidenceReference> rows = jdbc.query("""
                SELECT p.protected_object_id, p.tenant_draft_id
                  FROM tenants t
                  JOIN ycs_crypto_protected_objects p ON p.protected_object_id = %s
                 WHERE t.id = ? AND p.object_state = 'CLAIMED'
                """.formatted(kind.column), (rs, row) -> new EvidenceReference(
                rs.getString("protected_object_id"), rs.getString("tenant_draft_id")), tenantId);
        if (rows.size() != 1) throw Failure.denied();
        return rows.getFirst();
    }

    public enum EvidenceKind {
        BUSINESS_LICENSE("t.business_license_url", PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE),
        REPRESENTATIVE_ID_FRONT("t.legal_rep_id_front_url", PrivateObjectStorePort.ObjectPurpose.REPRESENTATIVE_ID_FRONT),
        REPRESENTATIVE_ID_BACK("t.legal_rep_id_back_url", PrivateObjectStorePort.ObjectPurpose.REPRESENTATIVE_ID_BACK),
        SHORT_LINK_DOMAIN_PROOF("t.shortlink_domain_proof_url", PrivateObjectStorePort.ObjectPurpose.SHORT_LINK_DOMAIN_PROOF),
        TRADEMARK_PROOF("t.trademark_proof_url", PrivateObjectStorePort.ObjectPurpose.TRADEMARK_PROOF);
        private final String column;
        private final PrivateObjectStorePort.ObjectPurpose objectPurpose;
        EvidenceKind(String column, PrivateObjectStorePort.ObjectPurpose objectPurpose) {
            this.column = column; this.objectPurpose = objectPurpose;
        }
    }

    public static final class EvidenceContent {
        private final String mediaType;
        private final byte[] bytes;
        public EvidenceContent(String mediaType, byte[] bytes) { this.mediaType = mediaType; this.bytes = bytes.clone(); }
        public String mediaType() { return mediaType; }
        public byte[] bytes() { return bytes.clone(); }
        @Override public String toString() { return "EvidenceContent[mediaType=" + mediaType + ", bytes=[redacted]]"; }
    }
    private record EvidenceReference(String objectId, String tenantDraftId) { }
    public static final class Failure extends RuntimeException {
        private Failure(String code) { super(code, null, false, false); }
        static Failure denied() { return new Failure("QUALIFICATION_EVIDENCE_DENIED"); }
        public static Failure unavailable() { return new Failure("QUALIFICATION_EVIDENCE_UNAVAILABLE"); }
    }
}
