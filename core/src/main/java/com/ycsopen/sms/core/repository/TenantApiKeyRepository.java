package com.ycsopen.sms.core.repository;

import com.ycsopen.sms.core.domain.entity.TenantApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TenantApiKeyRepository extends JpaRepository<TenantApiKey, Long> {

    /**
     * Authentication lookup that deliberately excludes {@code app_secret_encrypted} until the
     * complete body-signature path owns an explicit protected-secret read.
     */
    @Query("""
            select apiKey.id as id, apiKey.tenantId as tenantId, apiKey.status as status
              from TenantApiKey apiKey
             where apiKey.appKey = :appKey
            """)
    Optional<AuthenticationProjection> findAuthenticationByAppKey(@Param("appKey") String appKey);

    interface AuthenticationProjection {
        Long getId();

        Long getTenantId();

        TenantApiKey.Status getStatus();
    }
}
