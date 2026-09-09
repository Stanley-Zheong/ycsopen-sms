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
            select apiKey.id as id,
                   apiKey.tenantId as tenantId,
                   apiKey.status as status,
                   apiKey.rateLimitPerSec as rateLimitPerSec,
                   apiKey.rateLimitPerMin as rateLimitPerMin,
                   apiKey.rateLimitPerHour as rateLimitPerHour,
                   apiKey.rateLimitPerDay as rateLimitPerDay
              from TenantApiKey apiKey
             where apiKey.appKey = :appKey
               and apiKey.status = 'ACTIVE'
               and (apiKey.expireTime is null or apiKey.expireTime > CURRENT_TIMESTAMP)
            """)
    Optional<AuthenticationProjection> findAuthenticationByAppKey(@Param("appKey") String appKey);

    @Query("""
            select apiKey.id as id,
                   apiKey.tenantId as tenantId,
                   apiKey.status as status,
                   apiKey.ipWhitelist as ipWhitelist,
                   apiKey.appSecretEncrypted as appSecretEncrypted,
                   apiKey.rateLimitPerSec as rateLimitPerSec,
                   apiKey.rateLimitPerMin as rateLimitPerMin,
                   apiKey.rateLimitPerHour as rateLimitPerHour,
                   apiKey.rateLimitPerDay as rateLimitPerDay
              from TenantApiKey apiKey
             where apiKey.appKey = :appKey
               and apiKey.status = 'ACTIVE'
               and (apiKey.expireTime is null or apiKey.expireTime > CURRENT_TIMESTAMP)
            """)
    Optional<SignatureAuthenticationProjection> findSignatureAuthenticationByAppKey(@Param("appKey") String appKey);

    interface AuthenticationProjection {
        Long getId();

        Long getTenantId();

        TenantApiKey.Status getStatus();

        Integer getRateLimitPerSec();

        Integer getRateLimitPerMin();

        Integer getRateLimitPerHour();

        Integer getRateLimitPerDay();
    }

    interface SignatureAuthenticationProjection extends AuthenticationProjection {
        String getIpWhitelist();

        byte[] getAppSecretEncrypted();
    }
}
