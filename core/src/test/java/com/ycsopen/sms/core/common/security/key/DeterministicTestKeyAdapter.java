package com.ycsopen.sms.core.common.security.key;

import com.ycsopen.sms.core.common.security.envelope.ProtectionContext;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiPredicate;

/**
 * Deterministic test adapter for behavior vectors only.
 * Evidence label: {@value #EVIDENCE_LABEL}. This is not KMS/HSM evidence.
 */
final class DeterministicTestKeyAdapter
        implements KeyProtectionPort, BlindIndexPort, OpaqueTokenDigestPort {

    static final String EVIDENCE_LABEL = "deterministic-test-adapter";
    private static final String KEK_REFERENCE = "test-kek-v1";
    private static final byte[] TOKEN_DIGEST_DOMAIN =
            "YCS-OPAQUE-TOKEN-DIGEST/v1\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] MOBILE_INDEX_DOMAIN =
            "mobile-sha256-v1\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WRAP_AAD_DOMAIN =
            "YCSE-WRAP-AAD\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PKCS11_PROVIDER = "pkcs11".getBytes(StandardCharsets.US_ASCII);

    private final byte[] wrappingMaterial = derive("field-encryption-kek-v1");
    private final List<VersionedKey> mobileKeys = List.of(
            new VersionedKey(1, TestState.RETIRING, derive("mobile-blind-index-v1")),
            new VersionedKey(2, TestState.ACTIVE, derive("mobile-blind-index-v2"))
    );
    private final Map<OpaqueTokenDigestPort.Purpose, List<VersionedKey>> tokenKeys;
    private final AtomicLong wrapReservations = new AtomicLong();
    private final WrapOperationAdmissionPort admission = keyReference -> {
        if (!KEK_REFERENCE.equals(keyReference)) {
            throw operationFailed();
        }
        return wrapReservations.incrementAndGet();
    };
    private final BiPredicate<byte[], byte[]> constantTimeComparison;

    DeterministicTestKeyAdapter() {
        this(MessageDigest::isEqual);
    }

    DeterministicTestKeyAdapter(BiPredicate<byte[], byte[]> constantTimeComparison) {
        if (constantTimeComparison == null) {
            throw new IllegalArgumentException("comparison is required");
        }
        this.constantTimeComparison = constantTimeComparison;
        this.tokenKeys = new EnumMap<>(OpaqueTokenDigestPort.Purpose.class);
        for (OpaqueTokenDigestPort.Purpose purpose : OpaqueTokenDigestPort.Purpose.values()) {
            tokenKeys.put(purpose, List.of(
                    new VersionedKey(1, TestState.RETIRING, derive(tokenLabel(purpose, 1))),
                    new VersionedKey(2, TestState.ACTIVE, derive(tokenLabel(purpose, 2))),
                    new VersionedKey(3, TestState.RETIRED, derive(tokenLabel(purpose, 3))),
                    new VersionedKey(4, TestState.REVOKED, derive(tokenLabel(purpose, 4)))
            ));
        }
    }

    @Override
    public WrappedDataKey wrap(byte[] dataEncryptionKey,
                               byte[] authenticatedHeader,
                               ProtectionContext semanticContext) {
        requireLength(dataEncryptionKey, DATA_ENCRYPTION_KEY_BYTES);
        requireHeaderKeyReference(authenticatedHeader, KEK_REFERENCE);
        byte[] aad = wrapAad(authenticatedHeader, semanticContext);
        long reservation = admission.reserve(KEK_REFERENCE);
        byte[] nonce = nonceFor(reservation);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(wrappingMaterial, "AES"),
                    new GCMParameterSpec(128, nonce));
            cipher.updateAAD(aad);
            return new WrappedDataKey(KEK_REFERENCE, nonce, cipher.doFinal(dataEncryptionKey));
        } catch (GeneralSecurityException exception) {
            throw operationFailed();
        }
    }

    @Override
    public byte[] unwrap(WrappedDataKey wrappedDataKey,
                         byte[] authenticatedHeader,
                         ProtectionContext semanticContext) {
        if (wrappedDataKey == null || !KEK_REFERENCE.equals(wrappedDataKey.keyReference())) {
            throw operationFailed();
        }
        requireHeaderKeyReference(authenticatedHeader, wrappedDataKey.keyReference());
        byte[] aad = wrapAad(authenticatedHeader, semanticContext);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(wrappingMaterial, "AES"),
                    new GCMParameterSpec(128, wrappedDataKey.wrapNonce()));
            cipher.updateAAD(aad);
            byte[] dek = cipher.doFinal(wrappedDataKey.wrappedDek());
            requireLength(dek, DATA_ENCRYPTION_KEY_BYTES);
            return dek;
        } catch (GeneralSecurityException exception) {
            throw operationFailed();
        }
    }

    @Override
    public BlindIndexPort.OrderedIndexes writeIndexes(String normalizedMobile,
                                                       BlindIndexPort.Context context) {
        return mobileIndexes(normalizedMobile, context, true);
    }

    @Override
    public BlindIndexPort.OrderedIndexes queryIndexes(String normalizedMobile,
                                                       BlindIndexPort.Context context) {
        return mobileIndexes(normalizedMobile, context, false);
    }

    @Override
    public VersionedTokenDigest issue(OpaqueTokenDigestPort.Purpose purpose,
                                      OpaqueTokenDigestPort.Binding binding,
                                      byte[] tokenSecret) {
        requireTokenInput(purpose, binding, tokenSecret);
        VersionedKey active = keysFor(purpose).stream()
                .filter(key -> key.state == TestState.ACTIVE)
                .reduce((left, right) -> {
                    throw operationFailed();
                })
                .orElseThrow(DeterministicTestKeyAdapter::operationFailed);
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
        VersionedKey storedVersion = keysFor(purpose).stream()
                .filter(key -> key.version == storedDigest.keyVersion())
                .findFirst()
                .orElse(null);
        if (storedVersion == null
                || (storedVersion.state != TestState.ACTIVE && storedVersion.state != TestState.RETIRING)) {
            return false;
        }
        VersionedTokenDigest candidate = digestToken(purpose, binding, tokenSecret, storedVersion);
        return constantTimeComparison.test(candidate.digest(), storedDigest.digest());
    }

    @Override
    public KeyHealth health() {
        return new KeyHealth(KeyHealth.Status.READY);
    }

    @Override
    public KeyHealth health(OpaqueTokenDigestPort.Purpose purpose) {
        if (purpose == null || keysFor(purpose).stream().noneMatch(key -> key.state == TestState.ACTIVE)) {
            return new KeyHealth(KeyHealth.Status.UNAVAILABLE);
        }
        return new KeyHealth(KeyHealth.Status.READY);
    }

    long reservedWrapCount() {
        return wrapReservations.get();
    }

    VersionedTokenDigest tokenDigestForVersion(OpaqueTokenDigestPort.Purpose purpose,
                                                long version,
                                                OpaqueTokenDigestPort.Binding binding,
                                                byte[] tokenSecret) {
        requireTokenInput(purpose, binding, tokenSecret);
        VersionedKey key = keysFor(purpose).stream()
                .filter(candidate -> candidate.version == version)
                .findFirst()
                .orElseThrow(DeterministicTestKeyAdapter::operationFailed);
        return digestToken(purpose, binding, tokenSecret, key);
    }

    private BlindIndexPort.OrderedIndexes mobileIndexes(String mobile,
                                                         BlindIndexPort.Context context,
                                                         boolean writes) {
        if (mobile == null || !mobile.matches("1[3-9][0-9]{9}") || context == null) {
            throw operationFailed();
        }
        byte[] historicalDigest = sha256(mobile.getBytes(StandardCharsets.US_ASCII));
        byte[] canonical = encodeMobileInput(context, historicalDigest);
        List<VersionedBlindIndex> values = new ArrayList<>();
        mobileKeys.stream()
                .filter(key -> key.state == TestState.ACTIVE
                        || key.state == TestState.RETIRING && (!writes || mixedWriterRequires(key.version)))
                .sorted(Comparator.comparingLong(key -> key.version))
                .forEach(key -> values.add(new VersionedBlindIndex(Math.toIntExact(key.version),
                        hmac(key.material, canonical))));
        return new BlindIndexPort.OrderedIndexes(values);
    }

    private static boolean mixedWriterRequires(long version) {
        return version == 1;
    }

    private static byte[] encodeMobileInput(BlindIndexPort.Context context, byte[] historicalDigest) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(MOBILE_INDEX_DOMAIN);
        writeLengthPrefixed(output, context.targetTypeBytes());
        writeLengthPrefixed(output, context.fieldBytes());
        writeLengthPrefixed(output, context.purposeBytes());
        writeLengthPrefixed(output, context.scopeBytes());
        output.writeBytes(historicalDigest);
        return output.toByteArray();
    }

    private VersionedTokenDigest digestToken(OpaqueTokenDigestPort.Purpose purpose,
                                              OpaqueTokenDigestPort.Binding binding,
                                              byte[] tokenSecret,
                                              VersionedKey key) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(TOKEN_DIGEST_DOMAIN);
        output.write(purpose.domainByte());
        writeLengthPrefixed(output, binding.tenantBytes());
        writeLengthPrefixed(output, binding.subjectBytes());
        writeLengthPrefixed(output, binding.resourceOrSessionBytes());
        output.writeBytes(tokenSecret);
        return new VersionedTokenDigest(purpose, key.version, hmac(key.material, output.toByteArray()));
    }

    private static byte[] wrapAad(byte[] authenticatedHeader, ProtectionContext semanticContext) {
        if (authenticatedHeader == null || authenticatedHeader.length < 26
                || authenticatedHeader.length > 57 || semanticContext == null) {
            throw operationFailed();
        }
        byte[] context = semanticContext.canonicalBytes();
        ByteBuffer output = ByteBuffer.allocate(WRAP_AAD_DOMAIN.length + 4 + authenticatedHeader.length
                + 4 + context.length);
        output.put(WRAP_AAD_DOMAIN);
        output.putInt(authenticatedHeader.length);
        output.put(authenticatedHeader);
        output.putInt(context.length);
        output.put(context);
        return output.array();
    }

    private static void requireHeaderKeyReference(byte[] authenticatedHeader, String expectedReference) {
        if (authenticatedHeader == null || authenticatedHeader.length < 26
                || Byte.toUnsignedInt(authenticatedHeader[9]) != PKCS11_PROVIDER.length) {
            throw operationFailed();
        }
        int keyReferenceLength = Byte.toUnsignedInt(authenticatedHeader[10]);
        if (keyReferenceLength < 1 || authenticatedHeader.length != 19 + PKCS11_PROVIDER.length
                + keyReferenceLength) {
            throw operationFailed();
        }
        for (int index = 0; index < PKCS11_PROVIDER.length; index++) {
            if (authenticatedHeader[19 + index] != PKCS11_PROVIDER[index]) {
                throw operationFailed();
            }
        }
        String actualReference = new String(authenticatedHeader,
                19 + PKCS11_PROVIDER.length, keyReferenceLength, StandardCharsets.US_ASCII);
        if (!expectedReference.equals(actualReference)) {
            throw operationFailed();
        }
    }

    private static byte[] nonceFor(long reservation) {
        if (reservation < 1) {
            throw operationFailed();
        }
        return ByteBuffer.allocate(WrappedDataKey.WRAP_NONCE_BYTES)
                .putInt(0x44544131)
                .putLong(reservation)
                .array();
    }

    private static byte[] hmac(byte[] material, byte[] input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(material, "HmacSHA256"));
            return mac.doFinal(input);
        } catch (GeneralSecurityException exception) {
            throw operationFailed();
        }
    }

    private static byte[] derive(String label) {
        return sha256(("YCS-TEST-KEY/v1\0" + label).getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (GeneralSecurityException exception) {
            throw operationFailed();
        }
    }

    private static void writeLengthPrefixed(ByteArrayOutputStream output, byte[] value) {
        if (value.length > 0xffff) {
            throw operationFailed();
        }
        output.write(value.length >>> 8);
        output.write(value.length);
        output.writeBytes(value);
    }

    private List<VersionedKey> keysFor(OpaqueTokenDigestPort.Purpose purpose) {
        List<VersionedKey> keys = tokenKeys.get(purpose);
        if (keys == null) {
            throw operationFailed();
        }
        return keys;
    }

    private static String tokenLabel(OpaqueTokenDigestPort.Purpose purpose, long version) {
        return "opaque-token-" + purpose.name().toLowerCase() + "-v" + version;
    }

    private static void requireTokenInput(OpaqueTokenDigestPort.Purpose purpose,
                                          OpaqueTokenDigestPort.Binding binding,
                                          byte[] tokenSecret) {
        if (!validTokenInput(purpose, binding, tokenSecret)) {
            throw operationFailed();
        }
    }

    private static boolean validTokenInput(OpaqueTokenDigestPort.Purpose purpose,
                                           OpaqueTokenDigestPort.Binding binding,
                                           byte[] tokenSecret) {
        return purpose != null && binding != null
                && tokenSecret != null && tokenSecret.length == TOKEN_SECRET_BYTES;
    }

    private static void requireLength(byte[] value, int expected) {
        if (value == null || value.length != expected) {
            throw operationFailed();
        }
    }

    private static IllegalStateException operationFailed() {
        return new IllegalStateException("test key operation failed");
    }

    private enum TestState {
        ACTIVE,
        RETIRING,
        RETIRED,
        REVOKED
    }

    private record VersionedKey(long version, TestState state, byte[] material) {
    }
}
