package com.ycsopen.sms.core.common.security.config;

import com.ycsopen.sms.core.common.security.envelope.ProtectionContext;
import com.ycsopen.sms.core.common.security.key.BlindIndexPort;
import com.ycsopen.sms.core.common.security.key.KeyHealth;
import com.ycsopen.sms.core.common.security.key.KeyProtectionPort;
import com.ycsopen.sms.core.common.security.key.OpaqueTokenDigestPort;
import com.ycsopen.sms.core.common.security.key.VersionedTokenDigest;
import com.ycsopen.sms.core.common.security.key.WrappedDataKey;
import com.ycsopen.sms.core.common.security.key.pkcs11.KekWrapUsageRepository;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11CryptoStorageProperties;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11FailureMapper;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11ProviderFactory;
import com.ycsopen.sms.core.common.security.key.pkcs11.SunPkcs11KeyAdapter;
import com.ycsopen.sms.core.common.security.object.DenyAllObjectAccessAuthorization;
import com.ycsopen.sms.core.common.security.object.ObjectAccessAuthorizationPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Constructor-wired production crypto storage. No key or credential value is a property. */
@Configuration(proxyBeanMethods = false)
public class CryptoStorageConfiguration {

    private static final String PREFIX = "ycsopen.security.crypto-storage.";

    @Bean(destroyMethod = "close")
    CryptoStorageRuntime cryptoStorageRuntime(Environment environment,
                                              JdbcTemplate jdbcTemplate,
                                              PlatformTransactionManager transactionManager) {
        CryptoStorageStartupVerifier.Settings settings = settings(environment);
        settings.validate();
        if (!settings.enabled()) {
            DisabledCryptoStorageAdapter disabled = new DisabledCryptoStorageAdapter();
            return new CryptoStorageRuntime(settings, disabled, disabled, disabled, disabled, null);
        }

        Pkcs11FailureMapper failureMapper = new Pkcs11FailureMapper();
        Pkcs11CryptoStorageProperties properties = new Pkcs11CryptoStorageProperties(
                settings.modulePath(), settings.allowedModulePaths(), settings.slotId(),
                settings.tokenIdentity(),
                CryptoStorageStartupVerifier.environmentCredential(settings.credentialReference()),
                settings.descriptors());
        Pkcs11ProviderFactory.Session session = new Pkcs11ProviderFactory(failureMapper).open(properties);
        try {
            SunPkcs11KeyAdapter adapter = new SunPkcs11KeyAdapter(session, properties,
                    new KekWrapUsageRepository(jdbcTemplate, transactionManager, failureMapper), failureMapper);
            return new CryptoStorageRuntime(settings, adapter, adapter, adapter, adapter, session);
        } catch (RuntimeException failure) {
            session.close();
            throw failure;
        }
    }

    @Bean
    KeyProtectionPort keyProtectionPort(CryptoStorageRuntime runtime) {
        return runtime.keyProtectionPort();
    }

    @Bean
    BlindIndexPort blindIndexPort(CryptoStorageRuntime runtime) {
        return runtime.blindIndexPort();
    }

    @Bean
    OpaqueTokenDigestPort opaqueTokenDigestPort(CryptoStorageRuntime runtime) {
        return runtime.opaqueTokenDigestPort();
    }

    @Bean
    @ConditionalOnMissingBean(ObjectAccessAuthorizationPort.class)
    ObjectAccessAuthorizationPort objectAccessAuthorizationPort() {
        return new DenyAllObjectAccessAuthorization();
    }

    @Bean
    CryptoStorageStartupVerifier cryptoStorageStartupVerifier(CryptoStorageRuntime runtime,
                                                              Environment environment) {
        return new CryptoStorageStartupVerifier(runtime.settings(), runtime.adapter(),
                Set.copyOf(Arrays.asList(environment.getActiveProfiles())));
    }

    static CryptoStorageStartupVerifier.Settings settings(Environment environment) {
        boolean enabled = environment.getProperty(PREFIX + "enabled", Boolean.class, false);
        return new CryptoStorageStartupVerifier.Settings(
                enabled,
                environment.getProperty(PREFIX + "adapter"),
                environment.getProperty(PREFIX + "provider-id"),
                path(environment.getProperty(PREFIX + "module-path")),
                paths(environment.getProperty(PREFIX + "allowed-module-paths")),
                environment.getProperty(PREFIX + "slot-id", Long.class, -1L),
                environment.getProperty(PREFIX + "token-identity"),
                environment.getProperty(PREFIX + "credential-source",
                        CryptoStorageStartupVerifier.CredentialSource.class),
                environment.getProperty(PREFIX + "credential-reference"),
                values(environment.getProperty(PREFIX + "mechanisms")),
                values(environment.getProperty(PREFIX + "key-attributes")),
                environment.getProperty(PREFIX + "rotation-required-at", Long.class, -1L),
                environment.getProperty(PREFIX + "hard-ceiling", Long.class, -1L),
                environment.getProperty(PREFIX + "aliases.field-encryption-kek"),
                environment.getProperty(PREFIX + "aliases.mobile-blind-index"),
                environment.getProperty(PREFIX + "aliases.object-capability-digest"),
                environment.getProperty(PREFIX + "aliases.registration-upload-digest"));
    }

    private static Path path(String value) {
        return value == null || value.isBlank() ? null : Path.of(value);
    }

    private static List<Path> paths(String value) {
        return values(value).stream().map(Path::of).toList();
    }

    private static Set<String> values(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(",", -1)).map(String::trim)
                .filter(part -> !part.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }
}

record CryptoStorageRuntime(CryptoStorageStartupVerifier.Settings settings,
                            Object adapter,
                            KeyProtectionPort keyProtectionPort,
                            BlindIndexPort blindIndexPort,
                            OpaqueTokenDigestPort opaqueTokenDigestPort,
                            AutoCloseable closeable) implements AutoCloseable {
    @Override
    public void close() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }
}

final class DisabledCryptoStorageAdapter
        implements KeyProtectionPort, BlindIndexPort, OpaqueTokenDigestPort {

    @Override
    public WrappedDataKey wrap(byte[] dataEncryptionKey, byte[] authenticatedHeader,
                               ProtectionContext semanticContext) {
        throw CryptoStorageStartupVerifier.invalid("enabled");
    }

    @Override
    public byte[] unwrap(WrappedDataKey wrappedDataKey, byte[] authenticatedHeader,
                         ProtectionContext semanticContext) {
        throw CryptoStorageStartupVerifier.invalid("enabled");
    }

    @Override
    public OrderedIndexes writeIndexes(String normalizedMobile, BlindIndexPort.Context context) {
        throw CryptoStorageStartupVerifier.invalid("enabled");
    }

    @Override
    public OrderedIndexes queryIndexes(String normalizedMobile, BlindIndexPort.Context context) {
        throw CryptoStorageStartupVerifier.invalid("enabled");
    }

    @Override
    public VersionedTokenDigest issue(OpaqueTokenDigestPort.Purpose purpose,
                                      Binding binding,
                                      byte[] tokenSecret) {
        throw CryptoStorageStartupVerifier.invalid("enabled");
    }

    @Override
    public boolean verify(OpaqueTokenDigestPort.Purpose purpose,
                          Binding binding,
                          byte[] tokenSecret,
                          VersionedTokenDigest storedDigest) {
        return false;
    }

    @Override
    public KeyHealth health() {
        return new KeyHealth(KeyHealth.Status.UNAVAILABLE);
    }

    @Override
    public KeyHealth health(OpaqueTokenDigestPort.Purpose purpose) {
        return new KeyHealth(KeyHealth.Status.UNAVAILABLE);
    }
}
