package com.ycsopen.sms.core.service.tenant;

import com.ycsopen.sms.core.domain.entity.Tenant;
import com.ycsopen.sms.core.repository.TenantRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/** Runs assisted inspection and records only bounded non-document facts. */
@Service
@ConditionalOnProperty(prefix = "ycsopen.object-store", name = "enabled", havingValue = "true")
public class QualificationInspectionService {
    private final TenantRepository tenants;
    private final QualificationEvidenceService evidence;
    private final QualificationInspectionSpi provider;
    private final Clock clock;
    private final TransactionOperations transactions;

    @Autowired
    public QualificationInspectionService(TenantRepository tenants, QualificationEvidenceService evidence,
                                          QualificationInspectionSpi provider,
                                          PlatformTransactionManager transactionManager) {
        this(tenants, evidence, provider, Clock.systemUTC(), new TransactionTemplate(transactionManager));
    }

    QualificationInspectionService(TenantRepository tenants, QualificationEvidenceService evidence,
                                   QualificationInspectionSpi provider, Clock clock,
                                   TransactionOperations transactions) {
        this.tenants = Objects.requireNonNull(tenants);
        this.evidence = Objects.requireNonNull(evidence);
        this.provider = Objects.requireNonNull(provider);
        this.clock = Objects.requireNonNull(clock);
        this.transactions = Objects.requireNonNull(transactions);
    }

    public TenantReviewService.ReviewView inspect(long tenantId, long expectedRevision,
                                                  TenantReviewService reviews) {
        Tenant snapshot = transactions.execute(status -> tenants.findById(tenantId)
                .orElseThrow(() -> failure("TENANT_NOT_FOUND")));
        requirePendingRevision(snapshot, expectedRevision);
        QualificationEvidenceService.EvidenceContent content = evidence.readForInspection(tenantId);
        QualificationInspectionSpi.Document document = new QualificationInspectionSpi.Document(
                content.mediaType(), content.bytes());
        QualificationInspectionSpi.Facts facts;
        try {
            facts = provider.inspect(document);
            validateFacts(facts);
        } catch (RuntimeException providerFailure) {
            transactions.executeWithoutResult(status -> recordFailure(tenantId, expectedRevision));
            throw failure("QUALIFICATION_INSPECTION_UNAVAILABLE");
        } finally {
            document.destroy();
        }
        Tenant saved = transactions.execute(status -> recordCompleted(tenantId, expectedRevision, facts));
        return reviews.view(saved, null);
    }

    private Tenant recordCompleted(long tenantId, long expectedRevision,
                                   QualificationInspectionSpi.Facts facts) {
        Tenant tenant = tenants.findByIdForUpdate(tenantId).orElseThrow(() -> failure("TENANT_NOT_FOUND"));
        requirePendingRevision(tenant, expectedRevision);
        tenant.setInspectionStatus(Tenant.InspectionStatus.COMPLETED);
        tenant.setInspectionCompanyName(facts.companyName().trim());
        tenant.setInspectionCreditCode(facts.creditCode());
        tenant.setInspectionConfidence(facts.confidence());
        tenant.setInspectionProviderRequestId(facts.requestId());
        tenant.setInspectionCompletedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        return tenants.saveAndFlush(tenant);
    }

    private void recordFailure(long tenantId, long expectedRevision) {
        Tenant tenant = tenants.findByIdForUpdate(tenantId).orElse(null);
        if (tenant == null || tenant.getVerificationStatus() != Tenant.VerificationStatus.PENDING
                || tenant.getQualificationRevision() != expectedRevision) return;
        tenant.setInspectionStatus(Tenant.InspectionStatus.FAILED);
        tenant.setInspectionCompanyName(null);
        tenant.setInspectionCreditCode(null);
        tenant.setInspectionConfidence(null);
        tenant.setInspectionProviderRequestId(null);
        tenant.setInspectionCompletedAt(null);
        tenants.saveAndFlush(tenant);
    }

    private static void requirePendingRevision(Tenant tenant, long expectedRevision) {
        if (tenant.getQualificationRevision() != expectedRevision) throw failure("QUALIFICATION_REVISION_STALE");
        if (tenant.getVerificationStatus() != Tenant.VerificationStatus.PENDING) throw failure("QUALIFICATION_NOT_PENDING");
    }

    private static void validateFacts(QualificationInspectionSpi.Facts facts) {
        if (facts == null || facts.companyName() == null || facts.companyName().isBlank()
                || facts.companyName().length() > 100 || facts.companyName().chars().anyMatch(Character::isISOControl)
                || facts.creditCode() == null || !facts.creditCode().matches("[0-9A-Z]{18}")
                || !Double.isFinite(facts.confidence()) || facts.confidence() < 0 || facts.confidence() > 1
                || facts.requestId() == null || !facts.requestId().matches("[A-Za-z0-9._:-]{1,100}")) {
            throw failure("QUALIFICATION_INSPECTION_UNAVAILABLE");
        }
    }

    private static InspectionFailure failure(String code) { return new InspectionFailure(code); }
    public static final class InspectionFailure extends RuntimeException {
        public InspectionFailure(String code) { super(code, null, false, false); }
    }
}
