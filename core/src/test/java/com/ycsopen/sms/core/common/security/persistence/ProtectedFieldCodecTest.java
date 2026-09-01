package com.ycsopen.sms.core.common.security.persistence;

import com.ycsopen.sms.core.common.security.envelope.CipherEnvelope;
import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.envelope.ProtectionContext;
import com.ycsopen.sms.core.common.security.envelope.ProtectionFailure;
import com.ycsopen.sms.core.common.security.key.KeyHealth;
import com.ycsopen.sms.core.common.security.key.KeyProtectionPort;
import com.ycsopen.sms.core.common.security.key.WrappedDataKey;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProtectedFieldCodecTest {

    private static final EnvelopeCodec ENVELOPE_CODEC = new EnvelopeCodec();
    private static final String KEY_REFERENCE = "test-kek-v1";

    @Test
    void exactContextRoundTripsAndEachEnvelopeUsesFreshDataAndWrapNonces() {
        RecordingKeyPort keyPort = new RecordingKeyPort(KEY_REFERENCE);
        ProtectedFieldCodec codec = codec(keyPort, KEY_REFERENCE);
        ProtectionContext context = databaseContext("message-42");
        byte[] plaintext = "短信安全 🔐".getBytes(StandardCharsets.UTF_8);

        byte[] first = codec.protect(plaintext, context, EnvelopeCodec.Target.DATABASE_FIELD);
        byte[] second = codec.protect(plaintext, context, EnvelopeCodec.Target.DATABASE_FIELD);

        assertThat(first).isNotEqualTo(second);
        assertThat(codec.unprotect(first, context, EnvelopeCodec.Target.DATABASE_FIELD))
                .containsExactly(plaintext);
        assertThat(codec.unprotect(second, context, EnvelopeCodec.Target.DATABASE_FIELD))
                .containsExactly(plaintext);
        CipherEnvelope firstEnvelope = ENVELOPE_CODEC.decode(first, EnvelopeCodec.Target.DATABASE_FIELD);
        CipherEnvelope secondEnvelope = ENVELOPE_CODEC.decode(second, EnvelopeCodec.Target.DATABASE_FIELD);
        assertThat(firstEnvelope.dataNonce()).isNotEqualTo(secondEnvelope.dataNonce());
        assertThat(firstEnvelope.wrapNonce()).isNotEqualTo(secondEnvelope.wrapNonce());
        assertThat(keyPort.wrapCalls).isEqualTo(2);
    }

    @Test
    void oneOpaqueWrapCallOwnsAdmissionNonceAndSerializedWrappedResult() {
        RecordingKeyPort keyPort = new RecordingKeyPort(KEY_REFERENCE);
        ProtectedFieldCodec codec = codec(keyPort, KEY_REFERENCE);
        ProtectionContext context = databaseContext("message-43");

        byte[] encoded = codec.protect(new byte[]{1, 2, 3}, context, EnvelopeCodec.Target.DATABASE_FIELD);
        CipherEnvelope envelope = ENVELOPE_CODEC.decode(encoded, EnvelopeCodec.Target.DATABASE_FIELD);

        assertThat(keyPort.wrapCalls).isEqualTo(1);
        assertThat(keyPort.events).containsExactly("admission", "nonce", "provider");
        assertThat(envelope.keyReference()).isEqualTo(KEY_REFERENCE);
        assertThat(envelope.wrapNonce()).containsExactly(keyPort.lastWrapped.wrapNonce());
        assertThat(envelope.wrappedDek()).containsExactly(keyPort.lastWrapped.wrappedDek());
        assertThat(keyPort.suppliedDekAfterCall).containsOnly(0);
        assertThat(keyPort.lastHeader).containsExactly(
                ENVELOPE_CODEC.authenticatedHeader(envelope, EnvelopeCodec.Target.DATABASE_FIELD));
    }

    @Test
    void wrongReturnedKeyReferenceFailsAfterOneWrapWithoutFallback() {
        RecordingKeyPort keyPort = new RecordingKeyPort(KEY_REFERENCE);
        keyPort.returnedKeyReference = "other-kek-v1";
        ProtectedFieldCodec codec = codec(keyPort, KEY_REFERENCE);

        assertInvalid(() -> codec.protect(new byte[]{1}, databaseContext("message-44"),
                EnvelopeCodec.Target.DATABASE_FIELD));
        assertThat(keyPort.wrapCalls).isEqualTo(1);
    }

    @Test
    void contextHeaderWrappedKeyNonceAndCiphertextTamperShareOneSanitizedFailure() {
        RecordingKeyPort keyPort = new RecordingKeyPort(KEY_REFERENCE);
        ProtectedFieldCodec codec = codec(keyPort, KEY_REFERENCE);
        ProtectionContext context = databaseContext("message-45");
        byte[] encoded = codec.protect(new byte[]{4, 5, 6}, context, EnvelopeCodec.Target.DATABASE_FIELD);

        assertInvalid(() -> codec.unprotect(encoded, databaseContext("other-message"),
                EnvelopeCodec.Target.DATABASE_FIELD));
        for (int offset : List.of(8, 36, 48, 96, encoded.length - 1)) {
            byte[] tampered = encoded.clone();
            tampered[offset] ^= 1;
            assertInvalid(() -> codec.unprotect(tampered, context, EnvelopeCodec.Target.DATABASE_FIELD));
        }
    }

    @Test
    void adapterOutageConsumesOneCallAndIsNeverRetried() {
        RecordingKeyPort keyPort = new RecordingKeyPort(KEY_REFERENCE);
        keyPort.failWrap = true;
        ProtectedFieldCodec codec = codec(keyPort, KEY_REFERENCE);

        assertInvalid(() -> codec.protect(new byte[]{7}, databaseContext("message-46"),
                EnvelopeCodec.Target.DATABASE_FIELD));
        assertThat(keyPort.wrapCalls).isEqualTo(1);
        assertThat(keyPort.events).containsExactly("admission");
    }

    @Test
    void selectedPurposeBoundsFailBeforeRandomnessOrWrapAdmission() {
        CountingSecureRandom random = new CountingSecureRandom();
        RecordingKeyPort keyPort = new RecordingKeyPort("kkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkk");
        ProtectedFieldCodec codec = new ProtectedFieldCodec(
                ENVELOPE_CODEC, keyPort, random, "kkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkk");

        byte[] maximumDatabaseValue = new byte[110];
        byte[] encoded = codec.protect(maximumDatabaseValue, databaseContext("message-47"),
                EnvelopeCodec.Target.DATABASE_FIELD);
        assertThat(encoded).hasSize(255);
        assertThat(keyPort.wrapCalls).isEqualTo(1);

        assertInvalid(() -> codec.protect(new byte[111], databaseContext("message-48"),
                EnvelopeCodec.Target.DATABASE_FIELD));
        assertInvalid(() -> codec.protect(new byte[5_242_881], protectedObjectContext("object-1"),
                EnvelopeCodec.Target.REPRESENTATIVE_ID_FRONT));
        assertInvalid(() -> codec.protect(new byte[10_485_761], snapshotContext(),
                EnvelopeCodec.Target.MYSQL_ENCRYPTED_SNAPSHOT_CHUNK));
        assertThat(keyPort.wrapCalls).isEqualTo(1);
        assertThat(random.calls).isEqualTo(2);
    }

    @Test
    void wrongTargetPurposeAndWrongKeyFailClosedWithoutPlaintextFallback() {
        RecordingKeyPort keyPort = new RecordingKeyPort(KEY_REFERENCE);
        ProtectedFieldCodec codec = codec(keyPort, KEY_REFERENCE);
        byte[] encoded = codec.protect(new byte[]{8, 9}, databaseContext("message-49"),
                EnvelopeCodec.Target.DATABASE_FIELD);

        assertInvalid(() -> codec.unprotect(encoded, protectedObjectContext("message-49"),
                EnvelopeCodec.Target.DATABASE_FIELD));
        RecordingKeyPort wrongKey = new RecordingKeyPort(KEY_REFERENCE, sequence(32, 77));
        assertInvalid(() -> codec(wrongKey, KEY_REFERENCE).unprotect(
                encoded, databaseContext("message-49"), EnvelopeCodec.Target.DATABASE_FIELD));
    }

    private static ProtectedFieldCodec codec(RecordingKeyPort keyPort, String keyReference) {
        return new ProtectedFieldCodec(ENVELOPE_CODEC, keyPort, new SecureRandom(), keyReference);
    }

    private static ProtectionContext databaseContext(String identity) {
        return new ProtectionContext(ProtectionContext.Purpose.DATABASE_FIELD,
                "crypto-storage-bootstrap", "message_task", "mobile", "tenant:17", identity);
    }

    private static ProtectionContext protectedObjectContext(String identity) {
        return new ProtectionContext(ProtectionContext.Purpose.PROTECTED_OBJECT,
                "crypto-storage-bootstrap", "registration-object", "representative-id-front",
                "tenant:17", identity);
    }

    private static ProtectionContext snapshotContext() {
        return new ProtectionContext(ProtectionContext.Purpose.MYSQL_ENCRYPTED_SNAPSHOT_CHUNK,
                "crypto-storage-bootstrap", "mysql-snapshot", "dump-chunk", "global",
                "migration_set_id=m1;snapshot_id=s1;chunk_index=0;final=1");
    }

    private static void assertInvalid(ThrowingCall call) {
        assertThatThrownBy(call::run)
                .isExactlyInstanceOf(ProtectionFailure.class)
                .hasMessage(ProtectionFailure.SANITIZED_MESSAGE)
                .hasNoCause()
                .satisfies(failure -> assertThat(((ProtectionFailure) failure).category())
                        .isEqualTo(ProtectionFailure.Category.PROTECTED_DATA_INVALID));
    }

    private static byte[] sequence(int length, int seed) {
        byte[] value = new byte[length];
        for (int index = 0; index < value.length; index++) {
            value[index] = (byte) (seed + index);
        }
        return value;
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }

    private static final class CountingSecureRandom extends SecureRandom {
        private int calls;

        @Override
        public void nextBytes(byte[] bytes) {
            calls++;
            for (int index = 0; index < bytes.length; index++) {
                bytes[index] = (byte) (calls + index);
            }
        }
    }

    private static final class RecordingKeyPort implements KeyProtectionPort {
        private static final byte[] WRAP_DOMAIN =
                "YCSE-WRAP-AAD\0".getBytes(StandardCharsets.US_ASCII);

        private final String expectedKeyReference;
        private final byte[] wrappingKey;
        private final List<String> events = new ArrayList<>();
        private int wrapCalls;
        private boolean failWrap;
        private String returnedKeyReference;
        private WrappedDataKey lastWrapped;
        private byte[] lastHeader;
        private byte[] suppliedDekAfterCall;

        private RecordingKeyPort(String expectedKeyReference) {
            this(expectedKeyReference, sequence(32, 31));
        }

        private RecordingKeyPort(String expectedKeyReference, byte[] wrappingKey) {
            this.expectedKeyReference = expectedKeyReference;
            this.returnedKeyReference = expectedKeyReference;
            this.wrappingKey = wrappingKey.clone();
        }

        @Override
        public WrappedDataKey wrap(byte[] dataEncryptionKey,
                                   byte[] authenticatedHeader,
                                   ProtectionContext semanticContext) {
            wrapCalls++;
            events.add("admission");
            if (failWrap) {
                throw new IllegalStateException("sensitive provider detail");
            }
            requireHeaderReference(authenticatedHeader, expectedKeyReference);
            lastHeader = authenticatedHeader.clone();
            events.add("nonce");
            byte[] nonce = ByteBuffer.allocate(12).putInt(0x57525031).putLong(wrapCalls).array();
            events.add("provider");
            byte[] wrapped = aesGcm(true, wrappingKey, nonce,
                    wrapAad(authenticatedHeader, semanticContext), dataEncryptionKey);
            lastWrapped = new WrappedDataKey(returnedKeyReference, nonce, wrapped);
            suppliedDekAfterCall = dataEncryptionKey;
            return lastWrapped;
        }

        @Override
        public byte[] unwrap(WrappedDataKey wrappedDataKey,
                             byte[] authenticatedHeader,
                             ProtectionContext semanticContext) {
            requireHeaderReference(authenticatedHeader, wrappedDataKey.keyReference());
            return aesGcm(false, wrappingKey, wrappedDataKey.wrapNonce(),
                    wrapAad(authenticatedHeader, semanticContext), wrappedDataKey.wrappedDek());
        }

        @Override
        public KeyHealth health() {
            return new KeyHealth(KeyHealth.Status.READY);
        }

        private static byte[] wrapAad(byte[] header, ProtectionContext context) {
            byte[] canonical = context.canonicalBytes();
            return ByteBuffer.allocate(WRAP_DOMAIN.length + 4 + header.length + 4 + canonical.length)
                    .put(WRAP_DOMAIN).putInt(header.length).put(header)
                    .putInt(canonical.length).put(canonical).array();
        }

        private static byte[] aesGcm(boolean encrypt,
                                     byte[] key,
                                     byte[] nonce,
                                     byte[] aad,
                                     byte[] input) {
            try {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,
                        new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
                cipher.updateAAD(aad);
                return cipher.doFinal(input);
            } catch (GeneralSecurityException failure) {
                throw new IllegalStateException("test key operation failed");
            }
        }

        private static void requireHeaderReference(byte[] header, String expected) {
            int keyLength = Byte.toUnsignedInt(header[10]);
            String actual = new String(header, 25, keyLength, StandardCharsets.US_ASCII);
            if (!MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII),
                    expected.getBytes(StandardCharsets.US_ASCII))) {
                throw new IllegalStateException("test key operation failed");
            }
        }
    }
}
