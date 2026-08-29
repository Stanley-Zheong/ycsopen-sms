package com.ycsopen.sms.core.repository;

import com.ycsopen.sms.core.domain.entity.TenantApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantApiKeyRepository extends JpaRepository<TenantApiKey, Long> {
    Optional<TenantApiKey> findByAppKey(String appKey);
}
