package com.ycsopen.sms.core.common.security.envelope;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Sole YCSE/v1 binary parser, serializer, capacity checker, and AAD encoder. */
public final class EnvelopeCodec {

    public static final int FIXED_HEADER_BYTES = 19;
    public static final int MAXIMUM_OVERHEAD_BYTES = 145;
    public static final int NONCE_BYTES = 12;
    public static final int WRAPPED_DEK_BYTES = 48;
    public static final int DATA_TAG_BYTES = 16;

    private static final byte[] MAGIC = {'Y', 'C', 'S', 'E'};
    private static final int VERSION = 1;
    private static final int DATA_ALGORITHM = 1;
    private static final int WRAP_ALGORITHM = 1;
    private static final int AAD_SCHEMA = 1;
    private static final int FLAGS = 0;
    private static final String PROVIDER_ID = "pkcs11";
    private static final byte[] PROVIDER_BYTES = PROVIDER_ID.getBytes(StandardCharsets.US_ASCII);
    private static final long MAXIMUM_U32 = 0xffff_ffffL;
    private static final byte[] DATA_AAD_PREFIX = "YCSE-DATA-AAD\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WRAP_AAD_PREFIX = "YCSE-WRAP-AAD\0".getBytes(StandardCharsets.US_ASCII);

    public enum Target {
        DATABASE_FIELD(110, 255, ProtectionContext.Purpose.DATABASE_FIELD),
        BUSINESS_LICENSE(10_485_760, 10_485_905, ProtectionContext.Purpose.PROTECTED_OBJECT),
        REPRESENTATIVE_ID_FRONT(5_242_880, 5_243_025, ProtectionContext.Purpose.PROTECTED_OBJECT),
        REPRESENTATIVE_ID_BACK(5_242_880, 5_243_025, ProtectionContext.Purpose.PROTECTED_OBJECT),
        SHORT_LINK_DOMAIN_PROOF(10_485_760, 10_485_905, ProtectionContext.Purpose.PROTECTED_OBJECT),
        TRADEMARK_PROOF(10_485_760, 10_485_905, ProtectionContext.Purpose.PROTECTED_OBJECT),
        MYSQL_ENCRYPTED_SNAPSHOT_CHUNK(
                10_485_760, 10_485_905, ProtectionContext.Purpose.MYSQL_ENCRYPTED_SNAPSHOT_CHUNK);

        private final long maximumPlaintextBytes;
        private final long maximumEnvelopeBytes;
        private final ProtectionContext.Purpose purpose;

        Target(long maximumPlaintextBytes, long maximumEnvelopeBytes, ProtectionContext.Purpose purpose) {
            this.maximumPlaintextBytes = maximumPlaintextBytes;
            this.maximumEnvelopeBytes = maximumEnvelopeBytes;
            this.purpose = purpose;
        }

        public long maximumPlaintextBytes() {
            return maximumPlaintextBytes;
        }

        public long maximumEnvelopeBytes() {
            return maximumEnvelopeBytes;
        }
    }

    public byte[] encode(CipherEnvelope envelope, Target target) {
        requireEnvelope(envelope);
        requireTarget(target);
        byte[] keyReference = keyReferenceBytes(envelope.keyReference());
        long completeLength = checkedCompleteLength(keyReference.length, envelope.ciphertextLength(), target);
        ByteBuffer output = ByteBuffer.allocate(checkedArrayLength(completeLength)).order(ByteOrder.BIG_ENDIAN);
        writeFixedHeader(output, keyReference.length, envelope.ciphertextLength());
        output.put(PROVIDER_BYTES);
        output.put(keyReference);
        output.put(envelope.wrapNonce());
        output.put(envelope.wrappedDek());
        output.put(envelope.dataNonce());
        output.put(envelope.ciphertext());
        return output.array();
    }

    public CipherEnvelope decode(byte[] input, Target target) {
        requireTarget(target);
        if (input == null || input.length < FIXED_HEADER_BYTES || input.length > target.maximumEnvelopeBytes()) {
            throw ProtectionFailure.invalid();
        }

        requireMagic(input);
        requireByte(input[4], VERSION);
        requireByte(input[5], DATA_ALGORITHM);
        requireByte(input[6], WRAP_ALGORITHM);
        requireByte(input[7], AAD_SCHEMA);
        requireByte(input[8], FLAGS);

        int providerLength = unsignedByte(input[9]);
        int keyReferenceLength = unsignedByte(input[10]);
        int wrapNonceLength = unsignedByte(input[11]);
        int dataNonceLength = unsignedByte(input[12]);
        int wrappedDekLength = unsignedShort(input, 13);
        long ciphertextLength = unsignedInt(input, 15);
        if (providerLength != PROVIDER_BYTES.length
                || keyReferenceLength < 1 || keyReferenceLength > 32
                || wrapNonceLength != NONCE_BYTES
                || dataNonceLength != NONCE_BYTES
                || wrappedDekLength != WRAPPED_DEK_BYTES) {
            throw ProtectionFailure.invalid();
        }

        long expectedLength = checkedCompleteLength(keyReferenceLength, ciphertextLength, target);
        if (expectedLength != input.length) {
            throw ProtectionFailure.invalid();
        }

        int offset = FIXED_HEADER_BYTES;
        requireProviderBytes(input, offset);
        offset += providerLength;
        String keyReference = decodeKeyReference(input, offset, keyReferenceLength);
        offset += keyReferenceLength;
        byte[] wrapNonce = Arrays.copyOfRange(input, offset, offset + wrapNonceLength);
        offset += wrapNonceLength;
        byte[] wrappedDek = Arrays.copyOfRange(input, offset, offset + wrappedDekLength);
        offset += wrappedDekLength;
        byte[] dataNonce = Arrays.copyOfRange(input, offset, offset + dataNonceLength);
        offset += dataNonceLength;
        byte[] ciphertext = Arrays.copyOfRange(input, offset, checkedArrayLength(expectedLength));
        return new CipherEnvelope(PROVIDER_ID, keyReference, wrapNonce, wrappedDek, dataNonce, ciphertext);
    }

    public CipherEnvelope decode(InputStream input, Long declaredLength, Target target) {
        requireTarget(target);
        if (input == null) {
            throw ProtectionFailure.invalid();
        }
        try {
            byte[] encoded = declaredLength == null
                    ? readUnknownLength(input, target)
                    : readDeclaredLength(input, declaredLength, target);
            return decode(encoded, target);
        } catch (IOException exception) {
            throw ProtectionFailure.invalid();
        }
    }

    public long maximumCompleteEnvelopeLength(String keyReference, long plaintextLength, Target target) {
        requireTarget(target);
        int keyReferenceLength = keyReferenceBytes(keyReference).length;
        if (plaintextLength < 0 || plaintextLength > target.maximumPlaintextBytes()) {
            throw ProtectionFailure.invalid();
        }
        long ciphertextLength = checkedAdd(plaintextLength, DATA_TAG_BYTES);
        return checkedCompleteLength(keyReferenceLength, ciphertextLength, target);
    }

    public byte[] authenticatedHeader(CipherEnvelope envelope, Target target) {
        requireEnvelope(envelope);
        return authenticatedHeaderForCiphertext(envelope.keyReference(), envelope.ciphertextLength(), target);
    }

    public byte[] authenticatedHeader(String keyReference, long plaintextLength, Target target) {
        requireTarget(target);
        if (plaintextLength < 0 || plaintextLength > target.maximumPlaintextBytes()) {
            throw ProtectionFailure.invalid();
        }
        return authenticatedHeaderForCiphertext(keyReference, checkedAdd(plaintextLength, DATA_TAG_BYTES), target);
    }

    public byte[] dataAad(CipherEnvelope envelope, ProtectionContext context, Target target) {
        requireEnvelope(envelope);
        return aad(DATA_AAD_PREFIX,
                dataAuthenticatedHeaderForCiphertext(envelope.keyReference(),
                        envelope.ciphertextLength(), target),
                context, target);
    }

    public byte[] dataAad(String keyReference, long plaintextLength, ProtectionContext context, Target target) {
        requireTarget(target);
        if (plaintextLength < 0 || plaintextLength > target.maximumPlaintextBytes()) {
            throw ProtectionFailure.invalid();
        }
        return aad(DATA_AAD_PREFIX,
                dataAuthenticatedHeaderForCiphertext(keyReference,
                        checkedAdd(plaintextLength, DATA_TAG_BYTES), target),
                context, target);
    }

    public byte[] wrapAad(CipherEnvelope envelope, ProtectionContext context, Target target) {
        return aad(WRAP_AAD_PREFIX, authenticatedHeader(envelope, target), context, target);
    }

    public byte[] wrapAad(String keyReference, long plaintextLength, ProtectionContext context, Target target) {
        return aad(WRAP_AAD_PREFIX, authenticatedHeader(keyReference, plaintextLength, target), context, target);
    }

    private byte[] authenticatedHeaderForCiphertext(String keyReference, long ciphertextLength, Target target) {
        requireTarget(target);
        byte[] keyReferenceBytes = keyReferenceBytes(keyReference);
        checkedCompleteLength(keyReferenceBytes.length, ciphertextLength, target);
        ByteBuffer header = ByteBuffer.allocate(FIXED_HEADER_BYTES + PROVIDER_BYTES.length + keyReferenceBytes.length)
                .order(ByteOrder.BIG_ENDIAN);
        writeFixedHeader(header, keyReferenceBytes.length, ciphertextLength);
        header.put(PROVIDER_BYTES);
        header.put(keyReferenceBytes);
        return header.array();
    }

    /**
     * Stable data-AEAD header. The public key reference is authenticated by the independent wrap
     * AEAD and is deliberately normalized out here so a DEK can be rewrapped without reusing the
     * data key/nonce to generate a second GCM tag. All immutable wire algorithms, sizes, provider
     * identity and the ciphertext length remain bound to the data tag.
     */
    private byte[] dataAuthenticatedHeaderForCiphertext(
            String keyReference, long ciphertextLength, Target target) {
        requireTarget(target);
        int keyReferenceLength = keyReferenceBytes(keyReference).length;
        checkedCompleteLength(keyReferenceLength, ciphertextLength, target);
        ByteBuffer header = ByteBuffer.allocate(FIXED_HEADER_BYTES + PROVIDER_BYTES.length)
                .order(ByteOrder.BIG_ENDIAN);
        writeFixedHeader(header, 0, ciphertextLength);
        header.put(PROVIDER_BYTES);
        return header.array();
    }

    private static byte[] aad(byte[] prefix, byte[] authenticatedHeader, ProtectionContext context, Target target) {
        requireTarget(target);
        if (context == null || context.purpose() != target.purpose) {
            throw ProtectionFailure.invalid();
        }
        byte[] canonicalContext = context.canonicalBytes();
        long size = prefix.length;
        size = checkedAdd(size, 4);
        size = checkedAdd(size, authenticatedHeader.length);
        size = checkedAdd(size, 4);
        size = checkedAdd(size, canonicalContext.length);
        ByteBuffer output = ByteBuffer.allocate(checkedArrayLength(size)).order(ByteOrder.BIG_ENDIAN);
        output.put(prefix);
        putU32(output, authenticatedHeader.length);
        output.put(authenticatedHeader);
        putU32(output, canonicalContext.length);
        output.put(canonicalContext);
        return output.array();
    }

    private static byte[] readDeclaredLength(InputStream input, long declaredLength, Target target) throws IOException {
        if (declaredLength < FIXED_HEADER_BYTES
                || declaredLength > target.maximumEnvelopeBytes()
                || declaredLength > Integer.MAX_VALUE) {
            throw ProtectionFailure.invalid();
        }
        int expected = (int) declaredLength;
        ByteArrayOutputStream output = new ByteArrayOutputStream(expected);
        copyBounded(input, output, expected);
        if (output.size() != expected || input.read() != -1) {
            throw ProtectionFailure.invalid();
        }
        return output.toByteArray();
    }

    private static byte[] readUnknownLength(InputStream input, Target target) throws IOException {
        int maximum = checkedArrayLength(target.maximumEnvelopeBytes());
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximum, 8_192));
        copyBounded(input, output, checkedArrayLength(checkedAdd(maximum, 1L)));
        if (output.size() > maximum || input.read() != -1) {
            throw ProtectionFailure.invalid();
        }
        return output.toByteArray();
    }

    private static void copyBounded(InputStream input, ByteArrayOutputStream output, int limit) throws IOException {
        byte[] buffer = new byte[8_192];
        while (output.size() < limit) {
            int remaining = limit - output.size();
            int read = input.read(buffer, 0, Math.min(buffer.length, remaining));
            if (read == -1) {
                return;
            }
            if (read == 0) {
                int one = input.read();
                if (one == -1) {
                    return;
                }
                output.write(one);
            } else {
                output.write(buffer, 0, read);
            }
        }
    }

    private static long checkedCompleteLength(int keyReferenceLength, long ciphertextLength, Target target) {
        requireTarget(target);
        if (keyReferenceLength < 1 || keyReferenceLength > 32
                || ciphertextLength < DATA_TAG_BYTES || ciphertextLength > MAXIMUM_U32) {
            throw ProtectionFailure.invalid();
        }
        long plaintextLength = ciphertextLength - DATA_TAG_BYTES;
        if (plaintextLength > target.maximumPlaintextBytes()) {
            throw ProtectionFailure.invalid();
        }
        long total = FIXED_HEADER_BYTES;
        total = checkedAdd(total, PROVIDER_BYTES.length);
        total = checkedAdd(total, keyReferenceLength);
        total = checkedAdd(total, NONCE_BYTES);
        total = checkedAdd(total, WRAPPED_DEK_BYTES);
        total = checkedAdd(total, NONCE_BYTES);
        total = checkedAdd(total, ciphertextLength);
        if (total > target.maximumEnvelopeBytes()) {
            throw ProtectionFailure.invalid();
        }
        return total;
    }

    private static void writeFixedHeader(ByteBuffer output, int keyReferenceLength, long ciphertextLength) {
        output.put(MAGIC);
        output.put((byte) VERSION);
        output.put((byte) DATA_ALGORITHM);
        output.put((byte) WRAP_ALGORITHM);
        output.put((byte) AAD_SCHEMA);
        output.put((byte) FLAGS);
        output.put((byte) PROVIDER_BYTES.length);
        output.put((byte) keyReferenceLength);
        output.put((byte) NONCE_BYTES);
        output.put((byte) NONCE_BYTES);
        output.putShort((short) WRAPPED_DEK_BYTES);
        putU32(output, ciphertextLength);
    }

    private static void putU32(ByteBuffer output, long value) {
        if (value < 0 || value > MAXIMUM_U32) {
            throw ProtectionFailure.invalid();
        }
        output.putInt((int) value);
    }

    private static void requireMagic(byte[] input) {
        for (int i = 0; i < MAGIC.length; i++) {
            if (input[i] != MAGIC[i]) {
                throw ProtectionFailure.invalid();
            }
        }
    }

    private static void requireByte(byte actual, int expected) {
        if (unsignedByte(actual) != expected) {
            throw ProtectionFailure.invalid();
        }
    }

    private static void requireProviderBytes(byte[] input, int offset) {
        if (offset < 0 || input.length - offset < PROVIDER_BYTES.length) {
            throw ProtectionFailure.invalid();
        }
        for (int i = 0; i < PROVIDER_BYTES.length; i++) {
            if (input[offset + i] != PROVIDER_BYTES[i]) {
                throw ProtectionFailure.invalid();
            }
        }
    }

    private static String decodeKeyReference(byte[] input, int offset, int length) {
        if (offset < 0 || length < 1 || input.length - offset < length) {
            throw ProtectionFailure.invalid();
        }
        for (int i = 0; i < length; i++) {
            int value = unsignedByte(input[offset + i]);
            if ((i == 0 && !isLowercaseAlphaNumeric(value))
                    || (i > 0 && !isKeyReferenceByte(value))) {
                throw ProtectionFailure.invalid();
            }
        }
        return new String(input, offset, length, StandardCharsets.US_ASCII);
    }

    static void requireProvider(String providerId) {
        if (!PROVIDER_ID.equals(providerId)) {
            throw ProtectionFailure.invalid();
        }
    }

    static void requireKeyReference(String keyReference) {
        keyReferenceBytes(keyReference);
    }

    private static byte[] keyReferenceBytes(String keyReference) {
        if (keyReference == null || keyReference.isEmpty() || keyReference.length() > 32) {
            throw ProtectionFailure.invalid();
        }
        byte[] bytes = keyReference.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length != keyReference.length() || !isLowercaseAlphaNumeric(unsignedByte(bytes[0]))) {
            throw ProtectionFailure.invalid();
        }
        for (int i = 1; i < bytes.length; i++) {
            if (!isKeyReferenceByte(unsignedByte(bytes[i]))) {
                throw ProtectionFailure.invalid();
            }
        }
        return bytes;
    }

    static void requireExactLength(byte[] bytes, int expectedLength) {
        if (bytes == null || bytes.length != expectedLength) {
            throw ProtectionFailure.invalid();
        }
    }

    static void requireCiphertext(byte[] ciphertext) {
        if (ciphertext == null || ciphertext.length < DATA_TAG_BYTES) {
            throw ProtectionFailure.invalid();
        }
    }

    private static void requireEnvelope(CipherEnvelope envelope) {
        if (envelope == null) {
            throw ProtectionFailure.invalid();
        }
        requireProvider(envelope.providerId());
        requireKeyReference(envelope.keyReference());
    }

    private static void requireTarget(Target target) {
        if (target == null) {
            throw ProtectionFailure.invalid();
        }
    }

    private static boolean isKeyReferenceByte(int value) {
        return isLowercaseAlphaNumeric(value) || value == '.' || value == '_' || value == '-';
    }

    private static boolean isLowercaseAlphaNumeric(int value) {
        return value >= 'a' && value <= 'z' || value >= '0' && value <= '9';
    }

    private static int unsignedByte(byte value) {
        return Byte.toUnsignedInt(value);
    }

    private static int unsignedShort(byte[] input, int offset) {
        return unsignedByte(input[offset]) << 8 | unsignedByte(input[offset + 1]);
    }

    private static long unsignedInt(byte[] input, int offset) {
        return (long) unsignedByte(input[offset]) << 24
                | (long) unsignedByte(input[offset + 1]) << 16
                | (long) unsignedByte(input[offset + 2]) << 8
                | unsignedByte(input[offset + 3]);
    }

    private static long checkedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw ProtectionFailure.invalid();
        }
    }

    private static int checkedArrayLength(long length) {
        if (length < 0 || length > Integer.MAX_VALUE) {
            throw ProtectionFailure.invalid();
        }
        return (int) length;
    }
}
