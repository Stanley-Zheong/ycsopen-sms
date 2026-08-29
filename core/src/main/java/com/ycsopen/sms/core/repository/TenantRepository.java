package com.ycsopen.sms.core.repository;

import com.ycsopen.sms.core.domain.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findByTenantNo(String tenantNo);
    Optional<Tenant> findByUnifiedSocialCreditCode(String code);
}
