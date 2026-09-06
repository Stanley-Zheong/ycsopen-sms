package com.ycsopen.sms.core.common.security.config;

import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.key.KeyProtectionPort;
import com.ycsopen.sms.core.common.security.key.OpaqueTokenDigestPort;
import com.ycsopen.sms.core.common.security.key.lifecycle.ActiveFieldKeyReference;
import com.ycsopen.sms.core.common.security.object.ObjectAccessAuthorizationPort;
import com.ycsopen.sms.core.common.security.object.ObjectCapabilityService;
import com.ycsopen.sms.core.common.security.object.ObjectStoreProperties;
import com.ycsopen.sms.core.common.security.object.ProtectedObjectMetadataRepository;
import com.ycsopen.sms.core.common.security.object.ProtectedObjectService;
import com.ycsopen.sms.core.common.security.object.S3PrivateObjectStoreAdapter;
import com.ycsopen.sms.core.common.security.object.TenantRegistrationObjectSessionService;
import com.ycsopen.sms.core.common.security.persistence.ProtectedFieldCodec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.security.SecureRandom;
import java.time.Clock;

/** Fail-closed production composition for private encrypted object storage. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ObjectStoreProperties.class)
@ConditionalOnProperty(prefix = "ycsopen.object-store", name = "enabled", havingValue = "true")
public class ObjectStorageConfiguration {

    @Bean(destroyMethod = "close")
    S3PrivateObjectStoreAdapter privateObjectStore(
            ObjectStoreProperties properties, CryptoStorageRuntime cryptoRuntime) {
        if (!cryptoRuntime.settings().enabled()) {
            throw CryptoStorageStartupVerifier.invalid("object-store-requires-crypto-storage");
        }
        return S3PrivateObjectStoreAdapter.create(properties);
    }

    @Bean
    ObjectCapabilityService objectCapabilityService(
            OpaqueTokenDigestPort tokenDigestPort,
            ProtectedObjectMetadataRepository metadataRepository,
            ObjectAccessAuthorizationPort authorizationPort) {
        return new ObjectCapabilityService(tokenDigestPort, metadataRepository,
                authorizationPort, Clock.systemUTC(), new SecureRandom());
    }

    @Bean
    ProtectedObjectService protectedObjectService(
            KeyProtectionPort keyProtectionPort,
            ActiveFieldKeyReference activeFieldKeyReference,
            S3PrivateObjectStoreAdapter objectStore,
            ProtectedObjectMetadataRepository metadataRepository,
            ObjectCapabilityService capabilityService) {
        ProtectedFieldCodec codec = new ProtectedFieldCodec(
                new EnvelopeCodec(), keyProtectionPort, new SecureRandom(),
                activeFieldKeyReference::current);
        return new ProtectedObjectService(codec, objectStore, metadataRepository,
                capabilityService, new SecureRandom(), Clock.systemUTC());
    }

    @Bean
    TenantRegistrationObjectSessionService tenantRegistrationObjectSessionService(
            OpaqueTokenDigestPort tokenDigestPort,
            ProtectedObjectService protectedObjectService,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        return new TenantRegistrationObjectSessionService(tokenDigestPort,
                protectedObjectService, jdbcTemplate, transactionManager,
                Clock.systemUTC(), new SecureRandom());
    }
}
