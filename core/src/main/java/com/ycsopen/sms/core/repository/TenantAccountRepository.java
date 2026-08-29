package com.ycsopen.sms.core.repository;

import com.ycsopen.sms.core.domain.entity.TenantAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantAccountRepository extends JpaRepository<TenantAccount, Long> {
    Optional<TenantAccount> findByTenantId(Long tenantId);
}
