package com.ycsopen.sms.core.common.security.envelope;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvelopeCodecTest {

    private static final EnvelopeCodec CODEC = new EnvelopeCodec();
    private static final String MAXIMUM_KEY_REFERENCE = "k1234567890123456789012345678901";

    @Test
    void byteExactGoldenEnvelopeRoundTrips() {
        CipherEnvelope envelope = envelope("kek.v1", 3);

        byte[] encoded = CODEC.encode(envelope, EnvelopeCodec.Target.DATABASE_FIELD);

        assertThat(HexFormat.of().formatHex(encoded)).isEqualTo(
                "59435345010101010006060c0c003000000013"
                        + "706b63733131"
                        + "6b656b2e7631"
                        + "0102030405060708090a0b0c"
                        + sequenceHex(0x21, 48)
                        + "4142434445464748494a4b4c"
                        + sequenceHex(0x61, 19));
        assertThat(CODEC.decode(encoded, EnvelopeCodec.Target.DATABASE_FIELD)).isEqualTo(envelope);
        assertThat(CODEC.authenticatedHeader(envelope, EnvelopeCodec.Target.DATABASE_FIELD))
                .containsExactly(Arrays.copyOf(encoded, EnvelopeCodec.FIXED_HEADER_BYTES + 12));
    }

    @ParameterizedTest(name = "fixed header offset {0} rejects value {1}")
    @MethodSource("invalidFixedHeaderMutations")
    void everyFixedHeaderFieldFailsClosed(int offset, int replacement) {
        byte[] encoded = CODEC.encode(envelope(MAXIMUM_KEY_REFERENCE, 110), EnvelopeCodec.Target.DATABASE_FIELD);
        encoded[offset] = (byte) replacement;

        assertInvalid(() -> CODEC.decode(encoded, EnvelopeCodec.Target.DATABASE_FIELD));
    }

    static Stream<Arguments> invalidFixedHeaderMutations() {
        return Stream.of(
                Arguments.of(0, 'X'), Arguments.of(4, 2), Arguments.of(5, 2),
                Arguments.of(6, 2), Arguments.of(7, 2), Arguments.of(8, 1),
                Arguments.of(9, 5), Arguments.of(9, 7), Arguments.of(10, 0), Arguments.of(10, 33),
                Arguments.of(11, 11), Arguments.of(12, 13), Arguments.of(13, 1),
                Arguments.of(14, 47), Arguments.of(15, 0x7f));
    }

    @ParameterizedTest
    @MethodSource("invalidKeyReferences")
    void providerAndEveryNoncanonicalKeyReferenceByteClassAreRejected(String keyReference) {
        assertInvalid(() -> envelope(keyReference, 1));
    }

    static Stream<String> invalidKeyReferences() {
        return Stream.of("", "UPPER", ".starts-with-dot", "contains/slash", "contains space",
                "nonascii-密钥", "k12345678901234567890123456789012");
    }

    @Test
    void providerBytesAreExactlyPkcs11() {
        byte[] encoded = CODEC.encode(envelope("kek.v1", 3), EnvelopeCodec.Target.DATABASE_FIELD);
        encoded[EnvelopeCodec.FIXED_HEADER_BYTES] = 'x';

        assertInvalid(() -> CODEC.decode(encoded, EnvelopeCodec.Target.DATABASE_FIELD));
        assertInvalid(() -> new CipherEnvelope("other", "kek.v1", new byte[12], new byte[48],
                new byte[12], new byte[16]));
    }

    @Test
    void bothNoncesWrappedDekCiphertextContextAndDomainMutationsFailAuthentication() throws Exception {
        byte[] plaintext = "row-bound".getBytes(StandardCharsets.UTF_8);
        byte[] dataKey = sequence(0x11, 32);
        byte[] wrapKey = sequence(0x31, 32);
        byte[] wrapNonce = sequence(0x51, 12);
        byte[] dataNonce = sequence(0x71, 12);
        ProtectionContext context = new ProtectionContext(ProtectionContext.Purpose.DATABASE_FIELD,
                "crypto-storage-bootstrap", "message_tasks", "mobile_encrypted", "tenant:42", "message_id=msg_01");
        byte[] wrapAad = CODEC.wrapAad("kek.v1", plaintext.length, context, EnvelopeCodec.Target.DATABASE_FIELD);
        byte[] dataAad = CODEC.dataAad("kek.v1", plaintext.length, context, EnvelopeCodec.Target.DATABASE_FIELD);
        byte[] wrappedDek = encryptGcm(wrapKey, wrapNonce, dataKey, wrapAad);
        byte[] ciphertext = encryptGcm(dataKey, dataNonce, plaintext, dataAad);
        CipherEnvelope envelope = new CipherEnvelope("pkcs11", "kek.v1", wrapNonce, wrappedDek, dataNonce, ciphertext);

        assertThat(decryptGcm(wrapKey, envelope.wrapNonce(), envelope.wrappedDek(), wrapAad)).isEqualTo(dataKey);
        assertThat(decryptGcm(dataKey, envelope.dataNonce(), envelope.ciphertext(), dataAad)).isEqualTo(plaintext);

        assertInvalid(() -> authenticate(mutated(envelope, 0), wrapKey, context));
        assertInvalid(() -> authenticate(mutated(envelope, 1), wrapKey, context));
        assertInvalid(() -> authenticate(mutated(envelope, 2), wrapKey, context));
        assertInvalid(() -> authenticate(mutated(envelope, 3), wrapKey, context));

        ProtectionContext wrongContext = new ProtectionContext(ProtectionContext.Purpose.DATABASE_FIELD,
                "crypto-storage-bootstrap", "message_tasks", "mobile_encrypted", "tenant:43", "message_id=msg_01");
        assertInvalid(() -> authenticate(envelope, wrapKey, wrongContext));

        byte[] wrongDataDomain = dataAad.clone();
        wrongDataDomain[0] ^= 1;
        assertInvalid(() -> decryptSanitized(dataKey, dataNonce, ciphertext, wrongDataDomain));
        byte[] wrongWrapDomain = wrapAad.clone();
        wrongWrapDomain[0] ^= 1;
        assertInvalid(() -> decryptSanitized(wrapKey, wrapNonce, wrappedDek, wrongWrapDomain));
    }

    @Test
    void truncationTrailingBytesAndDeclaredActualMismatchFailClosed() {
        byte[] encoded = CODEC.encode(envelope("kek.v1", 3), EnvelopeCodec.Target.DATABASE_FIELD);

        assertInvalid(() -> CODEC.decode(Arrays.copyOf(encoded, encoded.length - 1),
                EnvelopeCodec.Target.DATABASE_FIELD));
        assertInvalid(() -> CODEC.decode(Arrays.copyOf(encoded, encoded.length + 1),
                EnvelopeCodec.Target.DATABASE_FIELD));
        assertInvalid(() -> CODEC.decode(new ByteArrayInputStream(encoded), (long) encoded.length - 1,
                EnvelopeCodec.Target.DATABASE_FIELD));
        assertInvalid(() -> CODEC.decode(new ByteArrayInputStream(encoded), (long) encoded.length + 1,
                EnvelopeCodec.Target.DATABASE_FIELD));
    }

    @Test
    void missingDeclaredLengthUsesPurposeBoundedReadAndRejectsTheExtraByte() {
        InputStream tooLong = new RepeatingInputStream(EnvelopeCodec.Target.DATABASE_FIELD.maximumEnvelopeBytes() + 1);

        assertInvalid(() -> CODEC.decode(tooLong, null, EnvelopeCodec.Target.DATABASE_FIELD));
    }

    @Test
    void oversizedDeclaredLengthsAndUnsignedU32HeaderFailBeforeLengthDerivedAllocation() {
        assertInvalid(() -> CODEC.decode(new RepeatingInputStream(0), 256L,
                EnvelopeCodec.Target.DATABASE_FIELD));

        byte[] encoded = CODEC.encode(envelope("kek.v1", 1), EnvelopeCodec.Target.DATABASE_FIELD);
        Arrays.fill(encoded, 15, 19, (byte) 0xff);
        assertInvalid(() -> CODEC.decode(encoded, EnvelopeCodec.Target.DATABASE_FIELD));
        assertInvalid(() -> CODEC.maximumCompleteEnvelopeLength(MAXIMUM_KEY_REFERENCE, Long.MAX_VALUE,
                EnvelopeCodec.Target.BUSINESS_LICENSE));
    }

    @Test
    void databaseCapacityIsExactAtPlaintext110AndEnvelope255() {
        CipherEnvelope exact = envelope(MAXIMUM_KEY_REFERENCE, 110);
        assertThat(CODEC.encode(exact, EnvelopeCodec.Target.DATABASE_FIELD)).hasSize(255);
        assertThat(CODEC.maximumCompleteEnvelopeLength(MAXIMUM_KEY_REFERENCE, 110,
                EnvelopeCodec.Target.DATABASE_FIELD)).isEqualTo(255);

        assertInvalid(() -> CODEC.maximumCompleteEnvelopeLength(MAXIMUM_KEY_REFERENCE, 111,
                EnvelopeCodec.Target.DATABASE_FIELD));
        assertInvalid(() -> CODEC.decode(new RepeatingInputStream(0), 256L,
                EnvelopeCodec.Target.DATABASE_FIELD));
    }

    @ParameterizedTest
    @MethodSource("boundedTargets")
    void everyObjectAndSnapshotTargetHasExactPlaintextAndEnvelopeCeilings(
            EnvelopeCodec.Target target, long plaintextLimit, long envelopeLimit) {
        assertThat(target.maximumPlaintextBytes()).isEqualTo(plaintextLimit);
        assertThat(target.maximumEnvelopeBytes()).isEqualTo(envelopeLimit);
        assertThat(CODEC.maximumCompleteEnvelopeLength(MAXIMUM_KEY_REFERENCE, plaintextLimit, target))
                .isEqualTo(envelopeLimit);
        assertInvalid(() -> CODEC.maximumCompleteEnvelopeLength(MAXIMUM_KEY_REFERENCE, plaintextLimit + 1, target));
        assertInvalid(() -> CODEC.decode(new RepeatingInputStream(0), envelopeLimit + 1, target));
    }

    static Stream<Arguments> boundedTargets() {
        return Stream.of(
                Arguments.of(EnvelopeCodec.Target.BUSINESS_LICENSE, 10_485_760L, 10_485_905L),
                Arguments.of(EnvelopeCodec.Target.REPRESENTATIVE_ID_FRONT, 5_242_880L, 5_243_025L),
                Arguments.of(EnvelopeCodec.Target.REPRESENTATIVE_ID_BACK, 5_242_880L, 5_243_025L),
                Arguments.of(EnvelopeCodec.Target.SHORT_LINK_DOMAIN_PROOF, 10_485_760L, 10_485_905L),
                Arguments.of(EnvelopeCodec.Target.TRADEMARK_PROOF, 10_485_760L, 10_485_905L),
                Arguments.of(EnvelopeCodec.Target.MYSQL_ENCRYPTED_SNAPSHOT_CHUNK, 10_485_760L, 10_485_905L));
    }

    @Test
    void maximumOverheadIsExactly145Bytes() {
        assertThat(EnvelopeCodec.MAXIMUM_OVERHEAD_BYTES).isEqualTo(145);
        assertThat(CODEC.maximumCompleteEnvelopeLength(MAXIMUM_KEY_REFERENCE, 0,
                EnvelopeCodec.Target.BUSINESS_LICENSE)).isEqualTo(145);
    }

    @Test
    void mutableInputsAndOutputsCannotChangeAnEnvelope() {
        byte[] wrapNonce = sequence(1, 12);
        byte[] wrappedDek = sequence(2, 48);
        byte[] dataNonce = sequence(3, 12);
        byte[] ciphertext = sequence(4, 17);
        CipherEnvelope envelope = new CipherEnvelope("pkcs11", "kek.v1", wrapNonce, wrappedDek, dataNonce, ciphertext);

        wrapNonce[0] = 99;
        wrappedDek[0] = 99;
        dataNonce[0] = 99;
        ciphertext[0] = 99;
        byte[] read = envelope.ciphertext();
        read[0] = 88;

        assertThat(envelope.wrapNonce()[0]).isEqualTo((byte) 1);
        assertThat(envelope.wrappedDek()[0]).isEqualTo((byte) 2);
        assertThat(envelope.dataNonce()[0]).isEqualTo((byte) 3);
        assertThat(envelope.ciphertext()[0]).isEqualTo((byte) 4);
    }

    private static CipherEnvelope envelope(String keyReference, int plaintextLength) {
        return new CipherEnvelope("pkcs11", keyReference, sequence(1, 12), sequence(0x21, 48),
                sequence(0x41, 12), sequence(0x61, plaintextLength + 16));
    }

    private static CipherEnvelope mutated(CipherEnvelope envelope, int component) {
        byte[] wrapNonce = envelope.wrapNonce();
        byte[] wrappedDek = envelope.wrappedDek();
        byte[] dataNonce = envelope.dataNonce();
        byte[] ciphertext = envelope.ciphertext();
        switch (component) {
            case 0 -> wrapNonce[0] ^= 1;
            case 1 -> wrappedDek[0] ^= 1;
            case 2 -> dataNonce[0] ^= 1;
            case 3 -> ciphertext[0] ^= 1;
            default -> throw new IllegalArgumentException("unknown component");
        }
        return new CipherEnvelope(envelope.providerId(), envelope.keyReference(), wrapNonce, wrappedDek, dataNonce, ciphertext);
    }

    private static void authenticate(CipherEnvelope envelope, byte[] wrapKey, ProtectionContext context) {
        byte[] wrapAad = CODEC.wrapAad(envelope, context, EnvelopeCodec.Target.DATABASE_FIELD);
        byte[] dataKey = decryptSanitized(wrapKey, envelope.wrapNonce(), envelope.wrappedDek(), wrapAad);
        byte[] dataAad = CODEC.dataAad(envelope, context, EnvelopeCodec.Target.DATABASE_FIELD);
        decryptSanitized(dataKey, envelope.dataNonce(), envelope.ciphertext(), dataAad);
    }

    private static byte[] encryptGcm(byte[] key, byte[] nonce, byte[] plaintext, byte[] aad) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(aad);
        return cipher.doFinal(plaintext);
    }

    private static byte[] decryptGcm(byte[] key, byte[] nonce, byte[] ciphertext, byte[] aad) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(aad);
        return cipher.doFinal(ciphertext);
    }

    private static byte[] decryptSanitized(byte[] key, byte[] nonce, byte[] ciphertext, byte[] aad) {
        try {
            return decryptGcm(key, nonce, ciphertext, aad);
        } catch (Exception exception) {
            throw ProtectionFailure.invalid();
        }
    }

    private static byte[] sequence(int first, int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) (first + i);
        }
        return bytes;
    }

    private static String sequenceHex(int first, int length) {
        return HexFormat.of().formatHex(sequence(first, length));
    }

    private static void assertInvalid(ThrowingCall call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ProtectionFailure.class)
                .hasMessage(ProtectionFailure.SANITIZED_MESSAGE)
                .extracting(error -> ((ProtectionFailure) error).category())
                .isEqualTo(ProtectionFailure.Category.PROTECTED_DATA_INVALID);
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run() throws Exception;
    }

    private static final class RepeatingInputStream extends InputStream {
        private long remaining;

        private RepeatingInputStream(long remaining) {
            this.remaining = remaining;
        }

        @Override
        public int read() {
            if (remaining == 0) {
                return -1;
            }
            remaining--;
            return 0;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            if (remaining == 0) {
                return -1;
            }
            int read = (int) Math.min(remaining, length);
            Arrays.fill(bytes, offset, offset + read, (byte) 0);
            remaining -= read;
            return read;
        }
    }
}
