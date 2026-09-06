package com.ycsopen.sms.core.common.security.config;

import com.ycsopen.sms.core.common.security.key.BlindIndexPort;
import com.ycsopen.sms.core.common.security.key.KeyProtectionPort;
import com.ycsopen.sms.core.common.security.key.OpaqueTokenDigestPort;
import com.ycsopen.sms.core.common.security.key.lifecycle.ActiveFieldKeyReference;
import com.ycsopen.sms.core.common.security.object.ObjectCapabilityService;
import com.ycsopen.sms.core.common.security.object.DenyAllObjectAccessAuthorization;
import com.ycsopen.sms.core.common.security.object.ObjectAccessAuthorizationPort;
import com.ycsopen.sms.core.common.security.object.ProtectedObjectMetadataRepository;
import com.ycsopen.sms.core.common.security.object.ProtectedObjectService;
import com.ycsopen.sms.core.common.security.object.S3PrivateObjectStoreAdapter;
import com.ycsopen.sms.core.common.security.object.TenantRegistrationObjectSessionService;
import com.ycsopen.sms.core.web.ProtectedObjectAccessController;
import com.ycsopen.sms.core.web.controller.TenantRegistrationObjectController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ObjectStorageConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestApplication.class)
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
            .withBean(PlatformTransactionManager.class,
                    () -> mock(PlatformTransactionManager.class))
            .withBean(ProtectedObjectMetadataRepository.class,
                    () -> mock(ProtectedObjectMetadataRepository.class))
            .withBean(KeyProtectionPort.class, () -> mock(KeyProtectionPort.class))
            .withBean(BlindIndexPort.class, () -> mock(BlindIndexPort.class))
            .withBean(OpaqueTokenDigestPort.class, () -> mock(OpaqueTokenDigestPort.class))
            .withBean(ObjectAccessAuthorizationPort.class,
                    DenyAllObjectAccessAuthorization::new)
            .withBean(ActiveFieldKeyReference.class, () -> {
                ActiveFieldKeyReference reference = mock(ActiveFieldKeyReference.class);
                when(reference.current()).thenReturn("field-kek.v1");
                return reference;
            })
            .withBean(CryptoStorageRuntime.class, ObjectStorageConfigurationTest::enabledRuntime);

    @Test
    void enabledConfigurationCreatesTheCompleteProductionRouteGraph() {
        runner.withPropertyValues(
                        "ycsopen.object-store.enabled=true",
                        "ycsopen.object-store.bucket=phase03-private",
                        "ycsopen.object-store.region=us-east-1",
                        "ycsopen.object-store.endpoint=http://127.0.0.1:9",
                        "ycsopen.object-store.allowed-endpoints=http://127.0.0.1:9",
                        "ycsopen.object-store.credential-provider=DEFAULT_CHAIN",
                        "ycsopen.object-store.path-style-access=true",
                        "ycsopen.object-store.allow-insecure-loopback=true")
                .run(context -> assertThat(context)
                        .hasSingleBean(S3PrivateObjectStoreAdapter.class)
                        .hasSingleBean(ObjectCapabilityService.class)
                        .hasSingleBean(ProtectedObjectService.class)
                        .hasSingleBean(TenantRegistrationObjectSessionService.class)
                        .hasSingleBean(ProtectedObjectAccessController.class)
                        .hasSingleBean(TenantRegistrationObjectController.class));
    }

    @Test
    void disabledConfigurationKeepsBothDocumentedRoutesAbsent() {
        runner.withPropertyValues("ycsopen.object-store.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(S3PrivateObjectStoreAdapter.class)
                        .doesNotHaveBean(ObjectCapabilityService.class)
                        .doesNotHaveBean(ProtectedObjectService.class)
                        .doesNotHaveBean(TenantRegistrationObjectSessionService.class)
                        .doesNotHaveBean(ProtectedObjectAccessController.class)
                        .doesNotHaveBean(TenantRegistrationObjectController.class));
    }

    private static CryptoStorageRuntime enabledRuntime() {
        CryptoStorageStartupVerifier.Settings settings = new CryptoStorageStartupVerifier.Settings(
                true, CryptoStorageStartupVerifier.ADAPTER_ID,
                CryptoStorageStartupVerifier.PROVIDER_ID, Path.of("/validated/pkcs11.so"),
                List.of(Path.of("/validated/pkcs11.so")), 1L, "phase03-production-test",
                CryptoStorageStartupVerifier.CredentialSource.ENVIRONMENT, "PHASE03_TEST_PIN",
                CryptoStorageStartupVerifier.REQUIRED_MECHANISMS,
                CryptoStorageStartupVerifier.REQUIRED_ATTRIBUTES, 983040L, 1048576L,
                CryptoStorageStartupVerifier.FIELD_KEK_ALIAS,
                CryptoStorageStartupVerifier.SNAPSHOT_RECOVERY_ALIAS,
                CryptoStorageStartupVerifier.SNAPSHOT_RECOVERY_REFERENCE,
                CryptoStorageStartupVerifier.MOBILE_INDEX_ALIAS,
                CryptoStorageStartupVerifier.OBJECT_DIGEST_ALIAS,
                CryptoStorageStartupVerifier.REGISTRATION_DIGEST_ALIAS);
        KeyProtectionPort key = mock(KeyProtectionPort.class);
        BlindIndexPort index = mock(BlindIndexPort.class);
        OpaqueTokenDigestPort digest = mock(OpaqueTokenDigestPort.class);
        return new CryptoStorageRuntime(settings, key, key, index, digest, null);
    }

    @Configuration(proxyBeanMethods = false)
    @Import({ObjectStorageConfiguration.class, ProtectedObjectAccessController.class,
            TenantRegistrationObjectController.class})
    static class TestApplication {
    }
}
