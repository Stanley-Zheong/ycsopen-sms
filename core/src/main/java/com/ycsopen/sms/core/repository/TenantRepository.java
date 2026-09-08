package com.ycsopen.sms.core.repository;

import com.ycsopen.sms.core.domain.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select tenant from Tenant tenant where tenant.id = :id")
    Optional<Tenant> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    /** Submission events contain only server-selected state names and authenticated actor identity. */
    @org.springframework.data.jpa.repository.Modifying
    @Query(value = """
            INSERT INTO tenant_qualification_events
                (tenant_id, action, before_verification_status, after_verification_status,
                 before_lifecycle_status, after_lifecycle_status, changed_fields, actor)
            VALUES (:tenantId, 'SUBMITTED', :beforeVerification, 'PENDING',
                    :lifecycle, :lifecycle, 'qualification', :actor)
            """, nativeQuery = true)
    int appendSubmissionEvent(@org.springframework.data.repository.query.Param("tenantId") Long tenantId,
                              @org.springframework.data.repository.query.Param("beforeVerification") String beforeVerification,
                              @org.springframework.data.repository.query.Param("lifecycle") String lifecycle,
                              @org.springframework.data.repository.query.Param("actor") String actor);

    @org.springframework.data.jpa.repository.Modifying
    @Query(value = """
            INSERT INTO tenant_qualification_events
                (tenant_id, action, before_verification_status, after_verification_status,
                 before_lifecycle_status, after_lifecycle_status, changed_fields, reason, actor)
            VALUES (:tenantId, :action, :beforeVerification, :afterVerification,
                    :beforeLifecycle, :afterLifecycle, 'qualification-decision', :reason, :actor)
            """, nativeQuery = true)
    int appendReviewEvent(@org.springframework.data.repository.query.Param("tenantId") Long tenantId,
                          @org.springframework.data.repository.query.Param("action") String action,
                          @org.springframework.data.repository.query.Param("beforeVerification") String beforeVerification,
                          @org.springframework.data.repository.query.Param("afterVerification") String afterVerification,
                          @org.springframework.data.repository.query.Param("beforeLifecycle") String beforeLifecycle,
                          @org.springframework.data.repository.query.Param("afterLifecycle") String afterLifecycle,
                          @org.springframework.data.repository.query.Param("reason") String reason,
                          @org.springframework.data.repository.query.Param("actor") String actor);

    /** Profile events contain server-selected field names and no changed values. */
    @org.springframework.data.jpa.repository.Modifying
    @Query(value = """
            INSERT INTO tenant_qualification_events
                (tenant_id, action, before_verification_status, after_verification_status,
                 before_lifecycle_status, after_lifecycle_status,
                 before_account_status, after_account_status, changed_fields, reason, actor)
            VALUES (:tenantId, 'PROFILE_UPDATED', :verification, :verification,
                    :lifecycle, :lifecycle, :accountStatus, :accountStatus,
                    :changedFields, :reason, :actor)
            """, nativeQuery = true)
    int appendProfileEvent(@org.springframework.data.repository.query.Param("tenantId") Long tenantId,
                           @org.springframework.data.repository.query.Param("verification") String verification,
                           @org.springframework.data.repository.query.Param("lifecycle") String lifecycle,
                           @org.springframework.data.repository.query.Param("accountStatus") String accountStatus,
                           @org.springframework.data.repository.query.Param("changedFields") String changedFields,
                           @org.springframework.data.repository.query.Param("reason") String reason,
                           @org.springframework.data.repository.query.Param("actor") String actor);

    /** Account events preserve qualification/lifecycle context without exposing business values. */
    @org.springframework.data.jpa.repository.Modifying
    @Query(value = """
            INSERT INTO tenant_qualification_events
                (tenant_id, action, before_verification_status, after_verification_status,
                 before_lifecycle_status, after_lifecycle_status,
                 before_account_status, after_account_status, changed_fields, reason, actor)
            VALUES (:tenantId, 'ACCOUNT_STATUS_UPDATED', :verification, :verification,
                    :lifecycle, :lifecycle, :beforeAccount, :afterAccount,
                    'accountStatus', :reason, :actor)
            """, nativeQuery = true)
    int appendAccountStatusEvent(@org.springframework.data.repository.query.Param("tenantId") Long tenantId,
                                 @org.springframework.data.repository.query.Param("verification") String verification,
                                 @org.springframework.data.repository.query.Param("lifecycle") String lifecycle,
                                 @org.springframework.data.repository.query.Param("beforeAccount") String beforeAccount,
                                 @org.springframework.data.repository.query.Param("afterAccount") String afterAccount,
                                 @org.springframework.data.repository.query.Param("reason") String reason,
                                 @org.springframework.data.repository.query.Param("actor") String actor);

    @Query(value = """
            SELECT id, action,
                   before_verification_status AS beforeVerificationStatus,
                   after_verification_status AS afterVerificationStatus,
                   before_lifecycle_status AS beforeLifecycleStatus,
                   after_lifecycle_status AS afterLifecycleStatus,
                   before_account_status AS beforeAccountStatus,
                   after_account_status AS afterAccountStatus,
                   changed_fields AS changedFields, reason, actor, created_at AS createdAt
              FROM tenant_qualification_events
             WHERE tenant_id = :tenantId
             ORDER BY id
            """, nativeQuery = true)
    List<QualificationEventProjection> findQualificationEvents(
            @org.springframework.data.repository.query.Param("tenantId") Long tenantId);
    Optional<Tenant> findByTenantNo(String tenantNo);
    Optional<Tenant> findByUnifiedSocialCreditCode(String code);

    /** Analytics enumeration that does not hydrate protected tenant bytes or object references. */
    @Query("select tenant.id as id from Tenant tenant")
    List<IdProjection> findAllIds();

    interface IdProjection {
        Long getId();
    }

    interface QualificationEventProjection {
        Long getId();
        String getAction();
        String getBeforeVerificationStatus();
        String getAfterVerificationStatus();
        String getBeforeLifecycleStatus();
        String getAfterLifecycleStatus();
        String getBeforeAccountStatus();
        String getAfterAccountStatus();
        String getChangedFields();
        String getReason();
        String getActor();
        java.time.LocalDateTime getCreatedAt();
    }
}
