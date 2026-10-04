package com.ycsopen.sms.core.common.security.config;

import com.ycsopen.sms.core.common.security.envelope.ProtectionContext;
import com.ycsopen.sms.core.common.security.key.BlindIndexPort;
import com.ycsopen.sms.core.common.security.key.KeyHealth;
import com.ycsopen.sms.core.common.security.key.KeyProtectionPort;
import com.ycsopen.sms.core.common.security.key.OpaqueTokenDigestPort;
import com.ycsopen.sms.core.common.security.key.VersionedBlindIndex;
import com.ycsopen.sms.core.common.security.key.VersionedTokenDigest;
import com.ycsopen.sms.core.common.security.key.WrappedDataKey;
import com.ycsopen.sms.core.common.security.key.pkcs11.KekWrapUsageRepository;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11CryptoStorageProperties;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11FailureMapper;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11ProviderFactory;
import com.ycsopen.sms.core.common.security.key.pkcs11.SunPkcs11KeyAdapter;
import com.ycsopen.sms.core.common.security.key.pkcs11.VersionedKeyDescriptorRegistry;
import com.ycsopen.sms.core.common.security.key.lifecycle.ActiveFieldKeyReference;
import com.ycsopen.sms.core.common.security.key.lifecycle.CryptoKeyLifecycleFactory;
import com.ycsopen.sms.core.common.security.key.lifecycle.FieldReferencePublicationFence;
import com.ycsopen.sms.core.common.security.key.lifecycle.JdbcFieldReferencePublicationFence;
import com.ycsopen.sms.core.common.security.key.lifecycle.KeyReferenceRepository;
import com.ycsopen.sms.core.common.security.key.lifecycle.EnvelopeReferenceInventory;
import com.ycsopen.sms.core.common.security.migration.snapshot.SnapshotChunkStore;
import com.ycsopen.sms.core.common.security.object.DenyAllObjectAccessAuthorization;
import com.ycsopen.sms.core.common.security.object.ObjectAccessAuthorizationPort;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
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
                                              PlatformTransactionManager transactionManager,
                                              KeyReferenceRepository keyReferences) {
        CryptoStorageStartupVerifier.Settings settings = settings(environment);
        settings.validate();
        if (!settings.enabled()) {
            if (devProfile(environment)) {
                LocalDevCryptoStorageAdapter local = new LocalDevCryptoStorageAdapter();
                return new CryptoStorageRuntime(settings, local, local, local, local, null);
            }
            DisabledCryptoStorageAdapter disabled = new DisabledCryptoStorageAdapter();
            return new CryptoStorageRuntime(settings, disabled, disabled, disabled, disabled, null);
        }

        Pkcs11FailureMapper failureMapper = new Pkcs11FailureMapper();
        Pkcs11CryptoStorageProperties properties = new Pkcs11CryptoStorageProperties(
                settings.modulePath(), settings.allowedModulePaths(), settings.slotId(),
                settings.tokenIdentity(),
                CryptoStorageStartupVerifier.environmentCredential(settings.credentialReference()),
                new VersionedKeyDescriptorRegistry(keyReferences,
                        VersionedKeyDescriptorRegistry.configured(
                                environment.getProperty(PREFIX + "key-descriptors"),
                                settings.descriptors())).load());
        Pkcs11ProviderFactory.Session session = new Pkcs11ProviderFactory(failureMapper).open(properties);
        try {
            SunPkcs11KeyAdapter adapter = new SunPkcs11KeyAdapter(session, properties,
                    new KekWrapUsageRepository(jdbcTemplate, transactionManager, failureMapper),
                    failureMapper, keyReferences);
            return new CryptoStorageRuntime(settings, adapter, adapter, adapter, adapter, session);
        } catch (RuntimeException failure) {
            session.close();
            throw failure;
        }
    }

    @Bean
    KeyReferenceRepository keyReferenceRepository(
            JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        return new KeyReferenceRepository.Jdbc(
                jdbcTemplate, new TransactionTemplate(transactionManager));
    }

    @Bean
    ActiveFieldKeyReference activeFieldKeyReference(KeyReferenceRepository references) {
        return new ActiveFieldKeyReference(references);
    }

    @Bean
    FieldReferencePublicationFence fieldReferencePublicationFence(JdbcTemplate jdbcTemplate) {
        return new JdbcFieldReferencePublicationFence(jdbcTemplate);
    }

    @Bean
    CryptoKeyLifecycleFactory cryptoKeyLifecycleFactory(
            KeyReferenceRepository references,
            KeyProtectionPort keyProtectionPort,
            FieldReferencePublicationFence publicationFence,
            JdbcTemplate jdbcTemplate,
            Environment environment) {
        Path snapshotRoot = path(environment.getProperty(PREFIX + "snapshot-store-root"));
        EnvelopeReferenceInventory.Source snapshotSource = snapshotRoot == null
                ? EnvelopeReferenceInventory.unavailableSnapshotEnvelopeSource()
                : EnvelopeReferenceInventory.snapshotEnvelopeSource(
                        jdbcTemplate, new SnapshotChunkStore.FileStore(snapshotRoot));
        return new CryptoKeyLifecycleFactory(
                references, keyProtectionPort, publicationFence, jdbcTemplate, snapshotSource);
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

    @Bean
    ApplicationRunner localDevCryptoStorageKeyReferenceSeeder(Environment environment, JdbcTemplate jdbcTemplate) {
        return args -> {
            if (!devProfile(environment)
                    || environment.getProperty(PREFIX + "enabled", Boolean.class, false)) {
                return;
            }
            seedReference(jdbcTemplate, "FIELD_ENCRYPTION_KEK", "dev-field-kek.v1");
            seedReference(jdbcTemplate, "MOBILE_BLIND_INDEX", "dev-mobile-index.v1");
            seedReference(jdbcTemplate, "OBJECT_CAPABILITY_DIGEST", "dev-object-digest.v1");
            seedReference(jdbcTemplate, "REGISTRATION_UPLOAD_DIGEST", "dev-registration-digest.v1");
        };
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
                environment.getProperty(PREFIX + "aliases.snapshot-recovery"),
                environment.getProperty(PREFIX + "references.snapshot-recovery"),
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

    private static boolean devProfile(Environment environment) {
        return Arrays.stream(environment.getActiveProfiles()).anyMatch("dev"::equals);
    }

    private static void seedReference(JdbcTemplate jdbcTemplate, String purpose, String reference) {
        Long existing = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM ycs_crypto_key_references
                WHERE purpose = ? AND key_version = 1
                """, Long.class, purpose);
        if (!Long.valueOf(0L).equals(existing)) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO ycs_crypto_key_references
                    (purpose, key_version, provider_id, provider_key_reference, key_state)
                VALUES (?, 1, 'pkcs11', ?, 'ACTIVE')
                """, purpose, reference);
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

final class LocalDevCryptoStorageAdapter
        implements KeyProtectionPort, BlindIndexPort, OpaqueTokenDigestPort {

    private static final String FIELD_KEY_REFERENCE = "dev-field-kek.v1";
    private static final int GCM_TAG_BITS = 128;
    private final SecureRandom secureRandom = new SecureRandom();
    private final byte[] wrapKey = sha256("ycsopen-sms-local-dev-wrap-key");
    private final byte[] mobileIndexKey = sha256("ycsopen-sms-local-dev-mobile-index-key");
    private final byte[] objectDigestKey = sha256("ycsopen-sms-local-dev-object-digest-key");
    private final byte[] uploadDigestKey = sha256("ycsopen-sms-local-dev-upload-digest-key");

    @Override
    public WrappedDataKey wrap(byte[] dataEncryptionKey, byte[] authenticatedHeader,
                               ProtectionContext semanticContext) {
        byte[] nonce = new byte[WrappedDataKey.WRAP_NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        byte[] wrapped = aesGcm(Cipher.ENCRYPT_MODE, wrapKey, nonce, authenticatedHeader, dataEncryptionKey);
        return new WrappedDataKey(FIELD_KEY_REFERENCE, nonce, wrapped);
    }

    @Override
    public byte[] unwrap(WrappedDataKey wrappedDataKey, byte[] authenticatedHeader,
                         ProtectionContext semanticContext) {
        return aesGcm(Cipher.DECRYPT_MODE, wrapKey, wrappedDataKey.wrapNonce(),
                authenticatedHeader, wrappedDataKey.wrappedDek());
    }

    @Override
    public OrderedIndexes writeIndexes(String normalizedMobile, BlindIndexPort.Context context) {
        return indexes(normalizedMobile, context);
    }

    @Override
    public OrderedIndexes queryIndexes(String normalizedMobile, BlindIndexPort.Context context) {
        return indexes(normalizedMobile, context);
    }

    @Override
    public VersionedTokenDigest issue(OpaqueTokenDigestPort.Purpose purpose,
                                      Binding binding,
                                      byte[] tokenSecret) {
        return new VersionedTokenDigest(purpose, 1, hmac(tokenKey(purpose),
                purpose.storagePurpose(), binding.tenant(), binding.subject(),
                binding.resourceOrSession(), HexFormat.of().formatHex(tokenSecret)));
    }

    @Override
    public boolean verify(OpaqueTokenDigestPort.Purpose purpose,
                          Binding binding,
                          byte[] tokenSecret,
                          VersionedTokenDigest storedDigest) {
        return issue(purpose, binding, tokenSecret).equals(storedDigest);
    }

    @Override
    public KeyHealth health() {
        return new KeyHealth(KeyHealth.Status.READY);
    }

    @Override
    public KeyHealth health(OpaqueTokenDigestPort.Purpose purpose) {
        return health();
    }

    private OrderedIndexes indexes(String normalizedMobile, BlindIndexPort.Context context) {
        return new OrderedIndexes(List.of(new VersionedBlindIndex(1, hmac(mobileIndexKey,
                context.targetType(), context.field(), context.purpose().wireValue(), context.scope(),
                normalizedMobile))));
    }

    private byte[] tokenKey(OpaqueTokenDigestPort.Purpose purpose) {
        return switch (purpose) {
            case OBJECT_CAPABILITY -> objectDigestKey;
            case REGISTRATION_UPLOAD -> uploadDigestKey;
        };
    }

    private static byte[] aesGcm(int mode, byte[] key, byte[] nonce, byte[] aad, byte[] input) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            if (aad != null) {
                cipher.updateAAD(aad);
            }
            return cipher.doFinal(input);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("LOCAL_DEV_CRYPTO_FAILED", failure);
        }
    }

    private static byte[] hmac(byte[] key, String... parts) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            for (String part : parts) {
                byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
                mac.update((byte) (bytes.length >>> 8));
                mac.update((byte) bytes.length);
                mac.update(bytes);
            }
            return mac.doFinal();
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("LOCAL_DEV_CRYPTO_FAILED", failure);
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("LOCAL_DEV_CRYPTO_FAILED", failure);
        }
    }
}
