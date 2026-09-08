package com.ycsopen.sms.core.repository;

import com.ycsopen.sms.core.domain.entity.TenantAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface TenantAccountRepository extends JpaRepository<TenantAccount, Long> {
    Optional<TenantAccount> findByTenantId(Long tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from TenantAccount account where account.tenantId = :tenantId")
    Optional<TenantAccount> findByTenantIdForUpdate(@Param("tenantId") Long tenantId);
}
