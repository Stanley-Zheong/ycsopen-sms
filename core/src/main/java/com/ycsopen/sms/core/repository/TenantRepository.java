package com.ycsopen.sms.core.repository;

import com.ycsopen.sms.core.domain.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findByTenantNo(String tenantNo);
    Optional<Tenant> findByUnifiedSocialCreditCode(String code);

    /** Analytics enumeration that does not hydrate protected tenant bytes or object references. */
    @Query("select tenant.id as id from Tenant tenant")
    List<IdProjection> findAllIds();

    interface IdProjection {
        Long getId();
    }
}
