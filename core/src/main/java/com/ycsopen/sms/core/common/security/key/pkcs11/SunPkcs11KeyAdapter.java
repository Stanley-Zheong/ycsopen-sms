package com.ycsopen.sms.core.common.security.key.pkcs11;

import com.ycsopen.sms.core.common.security.envelope.ProtectionContext;
import com.ycsopen.sms.core.common.security.key.BlindIndexPort;
import com.ycsopen.sms.core.common.security.key.KeyHealth;
import com.ycsopen.sms.core.common.security.key.KeyProtectionPort;
import com.ycsopen.sms.core.common.security.key.OpaqueTokenDigestPort;
import com.ycsopen.sms.core.common.security.key.VersionedBlindIndex;
import com.ycsopen.sms.core.common.security.key.VersionedTokenDigest;
import com.ycsopen.sms.core.common.security.key.WrappedDataKey;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.Provider;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Production opaque-key adapter backed only by Java 21 SunPKCS11 handles. */
public final class SunPkcs11KeyAdapter
        implements KeyProtectionPort, BlindIndexPort, OpaqueTokenDigestPort {

    private static final byte[] TOKEN_DIGEST_DOMAIN =
            "YCS-OPAQUE-TOKEN-DIGEST/v1\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] MOBILE_INDEX_DOMAIN =
            "mobile-sha256-v1\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WRAP_AAD_DOMAIN =
            "YCSE-WRAP-AAD\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PKCS11_PROVIDER = "pkcs11".getBytes(StandardCharsets.US_ASCII);

    private final Pkcs11ProviderFactory.Session session;
    private final KekWrapUsageRepository wrapUsageRepository;
    private final SecureRandom secureRandom;
    private final Pkcs11FailureMapper failureMapper;
    private final CryptoOperations operations;
    private final Pkcs11KeyDescriptor activeKek;
    private final Map<String, Pkcs11KeyDescriptor> keksByReference;
    private final List<Pkcs11KeyDescriptor> mobileKeys;
    private final Map<OpaqueTokenDigestPort.Purpose, List<Pkcs11KeyDescriptor>> tokenKeys;
    private final AtomicReference<KeyHealth.Status> runtimeStatus;

    public SunPkcs11KeyAdapter(Pkcs11ProviderFactory.Session session,
                               Pkcs11CryptoStorageProperties properties,
                               KekWrapUsageRepository wrapUsageRepository,
                               Pkcs11FailureMapper failureMapper) {
        this(session, properties, wrapUsageRepository, new SecureRandom(), failureMapper,
                new JcaCryptoOperations(session.provider()));
    }

    SunPkcs11KeyAdapter(Pkcs11ProviderFactory.Session session,
                        Pkcs11CryptoStorageProperties properties,
                        KekWrapUsageRepository wrapUsageRepository,
                        SecureRandom secureRandom,
                        Pkcs11FailureMapper failureMapper,
                        CryptoOperations operations) {
        this.session = Objects.requireNonNull(session, "session");
        this.wrapUsageRepository = Objects.requireNonNull(wrapUsageRepository, "wrapUsageRepository");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
        this.failureMapper = Objects.requireNonNull(failureMapper, "failureMapper");
        this.operations = Objects.requireNonNull(operations, "operations");

        List<Pkcs11KeyDescriptor> descriptors = properties.keys();
        this.activeKek = descriptors.stream()
                .filter(key -> key.purpose() == Pkcs11KeyDescriptor.Purpose.FIELD_ENCRYPTION_KEK
                        && key.state().permitsWrap())
                .reduce((left, right) -> {
                    throw failure(Pkcs11FailureMapper.Category.KEY_POLICY, left, null);
                })
                .orElseThrow(() -> failure(Pkcs11FailureMapper.Category.KEY_UNAVAILABLE, null, null));
        this.keksByReference = new HashMap<>();
        descriptors.stream()
                .filter(key -> key.purpose() == Pkcs11KeyDescriptor.Purpose.FIELD_ENCRYPTION_KEK)
                .forEach(key -> {
                    if (keksByReference.put(key.keyReference(), key) != null) {
                        throw failure(Pkcs11FailureMapper.Category.KEY_POLICY, key, null);
                    }
                });
        this.mobileKeys = descriptors.stream()
                .filter(key -> key.purpose() == Pkcs11KeyDescriptor.Purpose.MOBILE_BLIND_INDEX)
                .sorted(Comparator.comparingLong(Pkcs11KeyDescriptor::keyVersion))
                .toList();
        this.tokenKeys = new EnumMap<>(OpaqueTokenDigestPort.Purpose.class);
        tokenKeys.put(OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY,
                descriptorsFor(descriptors, Pkcs11KeyDescriptor.Purpose.OBJECT_CAPABILITY_DIGEST));
        tokenKeys.put(OpaqueTokenDigestPort.Purpose.REGISTRATION_UPLOAD,
                descriptorsFor(descriptors, Pkcs11KeyDescriptor.Purpose.REGISTRATION_UPLOAD_DIGEST));
        validatePurposeIsolation(descriptors);
        validateMechanisms(descriptors);
        this.runtimeStatus = new AtomicReference<>(activeKek.state()
                == Pkcs11KeyDescriptor.State.ROTATION_REQUIRED
                ? KeyHealth.Status.ROTATION_REQUIRED : KeyHealth.Status.READY);
    }

    @Override
    public WrappedDataKey wrap(byte[] dataEncryptionKey,
                               byte[] authenticatedHeader,
                               ProtectionContext semanticContext) {
        requireLength(dataEncryptionKey, DATA_ENCRYPTION_KEY_BYTES, activeKek);
        requireHeaderKeyReference(authenticatedHeader, activeKek.keyReference(), activeKek);
        byte[] aad = wrapAad(authenticatedHeader, semanticContext, activeKek);

        // This is the sole production ordering owner: reserve -> nonce -> provider.
        KekWrapUsageRepository.Reservation reservation = wrapUsageRepository.reserve(activeKek);
        if (reservation.rotationRequired()) {
            runtimeStatus.compareAndSet(KeyHealth.Status.READY, KeyHealth.Status.ROTATION_REQUIRED);
        }
        try {
            byte[] nonce = new byte[WrappedDataKey.WRAP_NONCE_BYTES];
            secureRandom.nextBytes(nonce);
            byte[] wrapped = operations.aesGcm(true, key(activeKek), nonce, aad, dataEncryptionKey);
            return new WrappedDataKey(activeKek.keyReference(), nonce, wrapped);
        } catch (RuntimeException exception) {
            markUnavailable();
            throw failure(Pkcs11FailureMapper.Category.OPERATION_FAILED, activeKek, exception);
        }
    }

    @Override
    public byte[] unwrap(WrappedDataKey wrappedDataKey,
                         byte[] authenticatedHeader,
                         ProtectionContext semanticContext) {
        if (wrappedDataKey == null) {
            throw failure(Pkcs11FailureMapper.Category.OPERATION_FAILED, null, null);
        }
        Pkcs11KeyDescriptor descriptor = keksByReference.get(wrappedDataKey.keyReference());
        if (descriptor == null || !descriptor.state().permitsUnwrap()) {
            throw failure(Pkcs11FailureMapper.Category.KEY_UNAVAILABLE, descriptor, null);
        }
        requireHeaderKeyReference(authenticatedHeader, descriptor.keyReference(), descriptor);
        byte[] aad = wrapAad(authenticatedHeader, semanticContext, descriptor);
        try {
            byte[] dek = operations.aesGcm(false, key(descriptor), wrappedDataKey.wrapNonce(),
                    aad, wrappedDataKey.wrappedDek());
            requireLength(dek, DATA_ENCRYPTION_KEY_BYTES, descriptor);
            return dek;
        } catch (RuntimeException exception) {
            markUnavailable();
            throw failure(Pkcs11FailureMapper.Category.OPERATION_FAILED, descriptor, exception);
        }
    }

    @Override
    public OrderedIndexes writeIndexes(String normalizedMobile, BlindIndexPort.Context context) {
        return mobileIndexes(normalizedMobile, context, true);
    }

    @Override
    public OrderedIndexes queryIndexes(String normalizedMobile, BlindIndexPort.Context context) {
        return mobileIndexes(normalizedMobile, context, false);
    }

    @Override
    public VersionedTokenDigest issue(OpaqueTokenDigestPort.Purpose purpose,
                                      OpaqueTokenDigestPort.Binding binding,
                                      byte[] tokenSecret) {
        requireTokenInput(purpose, binding, tokenSecret);
        Pkcs11KeyDescriptor active = keysFor(purpose).stream()
                .filter(key -> key.state().permitsDigestIssue())
                .reduce((left, right) -> {
                    throw failure(Pkcs11FailureMapper.Category.KEY_POLICY, left, null);
                })
                .orElseThrow(() -> failure(Pkcs11FailureMapper.Category.KEY_UNAVAILABLE, null, null));
        return digestToken(purpose, binding, tokenSecret, active);
    }

    @Override
    public boolean verify(OpaqueTokenDigestPort.Purpose purpose,
                          OpaqueTokenDigestPort.Binding binding,
                          byte[] tokenSecret,
                          VersionedTokenDigest storedDigest) {
        if (!validTokenInput(purpose, binding, tokenSecret) || storedDigest == null
                || storedDigest.purpose() != purpose) {
            return false;
        }
        Pkcs11KeyDescriptor storedVersion = keysFor(purpose).stream()
                .filter(key -> key.keyVersion() == storedDigest.keyVersion())
                .findFirst().orElse(null);
        if (storedVersion == null || !storedVersion.state().permitsDigestVerify()) {
            return false;
        }
        try {
            VersionedTokenDigest candidate = digestToken(purpose, binding, tokenSecret, storedVersion);
            return MessageDigest.isEqual(candidate.digest(), storedDigest.digest());
        } catch (Pkcs11FailureMapper.Pkcs11OperationException exception) {
            return false;
        }
    }

    @Override
    public KeyHealth health() {
        return new KeyHealth(runtimeStatus.get());
    }

    @Override
    public KeyHealth health(OpaqueTokenDigestPort.Purpose purpose) {
        if (purpose == null || runtimeStatus.get() == KeyHealth.Status.UNAVAILABLE
                || keysFor(purpose).stream().noneMatch(key -> key.state().permitsDigestIssue())) {
            return new KeyHealth(KeyHealth.Status.UNAVAILABLE);
        }
        return new KeyHealth(KeyHealth.Status.READY);
    }

    private OrderedIndexes mobileIndexes(String mobile,
                                         BlindIndexPort.Context context,
                                         boolean writes) {
        if (mobile == null || !mobile.matches("1[3-9][0-9]{9}") || context == null) {
            throw failure(Pkcs11FailureMapper.Category.OPERATION_FAILED, null, null);
        }
        byte[] historicalDigest = sha256(mobile.getBytes(StandardCharsets.US_ASCII));
        byte[] input = encodeMobileInput(context, historicalDigest);
        if (writes && mobileKeys.stream().noneMatch(key ->
                key.state() == Pkcs11KeyDescriptor.State.ACTIVE)) {
            throw failure(Pkcs11FailureMapper.Category.KEY_UNAVAILABLE, null, null);
        }
        List<VersionedBlindIndex> values = new ArrayList<>();
        mobileKeys.stream()
                .filter(key -> key.state() == Pkcs11KeyDescriptor.State.ACTIVE
                        || key.state() == Pkcs11KeyDescriptor.State.RETIRING)
                .forEach(key -> values.add(new VersionedBlindIndex(
                        Math.toIntExact(key.keyVersion()), hmac(key, input))));
        return new OrderedIndexes(values);
    }

    private VersionedTokenDigest digestToken(OpaqueTokenDigestPort.Purpose purpose,
                                              OpaqueTokenDigestPort.Binding binding,
                                              byte[] tokenSecret,
                                              Pkcs11KeyDescriptor descriptor) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(TOKEN_DIGEST_DOMAIN);
        output.write(purpose == OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY ? 1 : 2);
        writeLengthPrefixed(output, binding.tenant().getBytes(StandardCharsets.US_ASCII));
        writeLengthPrefixed(output, binding.subject().getBytes(StandardCharsets.US_ASCII));
        writeLengthPrefixed(output, binding.resourceOrSession().getBytes(StandardCharsets.US_ASCII));
        output.writeBytes(tokenSecret);
        return new VersionedTokenDigest(purpose, descriptor.keyVersion(), hmac(descriptor, output.toByteArray()));
    }

    private byte[] hmac(Pkcs11KeyDescriptor descriptor, byte[] input) {
        try {
            byte[] digest = operations.hmac(key(descriptor), input);
            requireLength(digest, VersionedTokenDigest.DIGEST_BYTES, descriptor);
            return digest;
        } catch (RuntimeException exception) {
            markUnavailable();
            throw failure(Pkcs11FailureMapper.Category.OPERATION_FAILED, descriptor, exception);
        }
    }

    private SecretKey key(Pkcs11KeyDescriptor descriptor) {
        Pkcs11ProviderFactory.TokenKey tokenKey = session.key(descriptor.alias());
        if (tokenKey == null) {
            throw failure(Pkcs11FailureMapper.Category.KEY_UNAVAILABLE, descriptor, null);
        }
        return tokenKey.handle();
    }

    private void validateMechanisms(List<Pkcs11KeyDescriptor> descriptors) {
        try {
            byte[] nonce = new byte[WrappedDataKey.WRAP_NONCE_BYTES];
            byte[] aad = "YCS-PKCS11-STARTUP-PROBE/v1".getBytes(StandardCharsets.US_ASCII);
            byte[] plaintext = new byte[DATA_ENCRYPTION_KEY_BYTES];
            byte[] ciphertext = operations.aesGcm(true, key(activeKek), nonce, aad, plaintext);
            byte[] recovered = operations.aesGcm(false, key(activeKek), nonce, aad, ciphertext);
            if (!MessageDigest.isEqual(plaintext, recovered)) {
                throw new IllegalStateException("AES-GCM probe mismatch");
            }
            for (Pkcs11KeyDescriptor descriptor : descriptors) {
                if (descriptor.purpose() != Pkcs11KeyDescriptor.Purpose.FIELD_ENCRYPTION_KEK
                        && operations.hmac(key(descriptor), aad).length != 32) {
                    throw new IllegalStateException("HMAC probe mismatch");
                }
            }
        } catch (RuntimeException exception) {
            throw failure(Pkcs11FailureMapper.Category.MECHANISM_UNAVAILABLE, activeKek, exception);
        }
    }

    private static List<Pkcs11KeyDescriptor> descriptorsFor(List<Pkcs11KeyDescriptor> descriptors,
                                                              Pkcs11KeyDescriptor.Purpose purpose) {
        return descriptors.stream().filter(key -> key.purpose() == purpose)
                .sorted(Comparator.comparingLong(Pkcs11KeyDescriptor::keyVersion)).toList();
    }

    private void validatePurposeIsolation(List<Pkcs11KeyDescriptor> descriptors) {
        Map<String, Pkcs11KeyDescriptor.Purpose> aliases = new HashMap<>();
        for (Pkcs11KeyDescriptor descriptor : descriptors) {
            Pkcs11KeyDescriptor.Purpose previous = aliases.put(
                    descriptor.alias().toLowerCase(java.util.Locale.ROOT), descriptor.purpose());
            if (previous != null && previous != descriptor.purpose()) {
                throw failure(Pkcs11FailureMapper.Category.KEY_POLICY, descriptor, null);
            }
        }
    }

    private List<Pkcs11KeyDescriptor> keysFor(OpaqueTokenDigestPort.Purpose purpose) {
        List<Pkcs11KeyDescriptor> descriptors = tokenKeys.get(purpose);
        if (descriptors == null) {
            throw failure(Pkcs11FailureMapper.Category.KEY_POLICY, null, null);
        }
        return descriptors;
    }

    private static byte[] encodeMobileInput(BlindIndexPort.Context context, byte[] digest) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(MOBILE_INDEX_DOMAIN);
        writeLengthPrefixed(output, context.targetType().getBytes(StandardCharsets.US_ASCII));
        writeLengthPrefixed(output, context.field().getBytes(StandardCharsets.US_ASCII));
        writeLengthPrefixed(output, context.purpose().wireValue().getBytes(StandardCharsets.US_ASCII));
        writeLengthPrefixed(output, context.scope().getBytes(StandardCharsets.US_ASCII));
        output.writeBytes(digest);
        return output.toByteArray();
    }

    private byte[] wrapAad(byte[] authenticatedHeader,
                           ProtectionContext context,
                           Pkcs11KeyDescriptor descriptor) {
        if (authenticatedHeader == null || authenticatedHeader.length < 26
                || authenticatedHeader.length > 57 || context == null) {
            throw failure(Pkcs11FailureMapper.Category.OPERATION_FAILED, descriptor, null);
        }
        byte[] canonical = context.canonicalBytes();
        return ByteBuffer.allocate(WRAP_AAD_DOMAIN.length + 4 + authenticatedHeader.length
                        + 4 + canonical.length)
                .put(WRAP_AAD_DOMAIN).putInt(authenticatedHeader.length).put(authenticatedHeader)
                .putInt(canonical.length).put(canonical).array();
    }

    private void requireHeaderKeyReference(byte[] header,
                                           String expectedReference,
                                           Pkcs11KeyDescriptor descriptor) {
        if (header == null || header.length < 26
                || Byte.toUnsignedInt(header[9]) != PKCS11_PROVIDER.length) {
            throw failure(Pkcs11FailureMapper.Category.OPERATION_FAILED, descriptor, null);
        }
        int keyReferenceLength = Byte.toUnsignedInt(header[10]);
        if (keyReferenceLength < 1 || header.length != 19 + PKCS11_PROVIDER.length + keyReferenceLength) {
            throw failure(Pkcs11FailureMapper.Category.OPERATION_FAILED, descriptor, null);
        }
        for (int index = 0; index < PKCS11_PROVIDER.length; index++) {
            if (header[19 + index] != PKCS11_PROVIDER[index]) {
                throw failure(Pkcs11FailureMapper.Category.OPERATION_FAILED, descriptor, null);
            }
        }
        String actual = new String(header, 19 + PKCS11_PROVIDER.length,
                keyReferenceLength, StandardCharsets.US_ASCII);
        if (!expectedReference.equals(actual)) {
            throw failure(Pkcs11FailureMapper.Category.KEY_POLICY, descriptor, null);
        }
    }

    private void requireTokenInput(OpaqueTokenDigestPort.Purpose purpose,
                                   OpaqueTokenDigestPort.Binding binding,
                                   byte[] tokenSecret) {
        if (!validTokenInput(purpose, binding, tokenSecret)) {
            throw failure(Pkcs11FailureMapper.Category.OPERATION_FAILED, null, null);
        }
    }

    private static boolean validTokenInput(OpaqueTokenDigestPort.Purpose purpose,
                                           OpaqueTokenDigestPort.Binding binding,
                                           byte[] tokenSecret) {
        return purpose != null && binding != null && tokenSecret != null
                && tokenSecret.length == TOKEN_SECRET_BYTES;
    }

    private void requireLength(byte[] value, int expected, Pkcs11KeyDescriptor descriptor) {
        if (value == null || value.length != expected) {
            throw failure(Pkcs11FailureMapper.Category.OPERATION_FAILED, descriptor, null);
        }
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    private static void writeLengthPrefixed(ByteArrayOutputStream output, byte[] value) {
        if (value.length > 0xffff) {
            throw new IllegalStateException("canonical input too large");
        }
        output.write(value.length >>> 8);
        output.write(value.length);
        output.writeBytes(value);
    }

    private Pkcs11FailureMapper.Pkcs11OperationException failure(
            Pkcs11FailureMapper.Category category,
            Pkcs11KeyDescriptor descriptor,
            Throwable cause) {
        return failureMapper.failure(category, descriptor, cause);
    }

    private void markUnavailable() {
        runtimeStatus.set(KeyHealth.Status.UNAVAILABLE);
    }

    interface CryptoOperations {
        byte[] aesGcm(boolean encrypt, SecretKey key, byte[] nonce, byte[] aad, byte[] input);

        byte[] hmac(SecretKey key, byte[] input);
    }

    private static final class JcaCryptoOperations implements CryptoOperations {
        private final Provider provider;

        private JcaCryptoOperations(Provider provider) {
            this.provider = provider;
        }

        @Override
        public byte[] aesGcm(boolean encrypt, SecretKey key, byte[] nonce, byte[] aad, byte[] input) {
            try {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", provider);
                cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, key,
                        new GCMParameterSpec(128, nonce));
                cipher.updateAAD(aad);
                return cipher.doFinal(input);
            } catch (GeneralSecurityException exception) {
                throw new IllegalStateException("PKCS11 AES-GCM failed");
            }
        }

        @Override
        public byte[] hmac(SecretKey key, byte[] input) {
            try {
                Mac mac = Mac.getInstance("HmacSHA256", provider);
                mac.init(key);
                return mac.doFinal(input);
            } catch (GeneralSecurityException exception) {
                throw new IllegalStateException("PKCS11 HMAC failed");
            }
        }
    }
}
