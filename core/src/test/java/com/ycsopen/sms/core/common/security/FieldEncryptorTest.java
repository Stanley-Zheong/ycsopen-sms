package com.ycsopen.sms.core.common.security;

import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.envelope.ProtectionFailure;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldEncryptorTest {

    private static final String KEY_BASE64 =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String DIFFERENT_KEY_BASE64 =
            "YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=";

    private final FieldEncryptor encryptor = new FieldEncryptor(KEY_BASE64);

    @Test
    void nullValuesRemainNull() {
        assertThat(encryptor.encrypt(null)).isNull();
        assertThat(encryptor.decrypt(null)).isNull();
    }

    @Test
    void emptyAndUnicodeTextRoundTrip() {
        assertThat(encryptor.decrypt(encryptor.encrypt(""))).isEmpty();

        String unicode = "短信安全 🔐 Καλημέρα";
        assertThat(encryptor.decrypt(encryptor.encrypt(unicode))).isEqualTo(unicode);
    }

    @Test
    void eachEncryptionUsesFreshIv() {
        String plaintext = "same plaintext";

        String first = encryptor.encrypt(plaintext);
        String second = encryptor.encrypt(plaintext);

        assertThat(first).isNotEqualTo(second);
        assertThat(encryptor.decrypt(first)).isEqualTo(plaintext);
        assertThat(encryptor.decrypt(second)).isEqualTo(plaintext);
    }

    @Test
    void tamperedCiphertextOrTagIsRejected() {
        byte[] tampered = Base64.getDecoder().decode(encryptor.encrypt("sensitive value"));
        tampered[tampered.length - 1] ^= 1;

        assertThatThrownBy(() -> encryptor.decrypt(Base64.getEncoder().encodeToString(tampered)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ciphertextCannotBeDecryptedWithDifferentKey() {
        String ciphertext = encryptor.encrypt("key-bound value");
        FieldEncryptor differentEncryptor = new FieldEncryptor(DIFFERENT_KEY_BASE64);

        assertThatThrownBy(() -> differentEncryptor.decrypt(ciphertext))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void malformedOrTooShortPayloadIsRejected() {
        assertThatThrownBy(() -> encryptor.decrypt("not-base64!"))
                .isInstanceOf(IllegalStateException.class);

        String shorterThanIv = Base64.getEncoder().encodeToString(new byte[11]);
        assertThatThrownBy(() -> encryptor.decrypt(shorterThanIv))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void legacyUnversionedPayloadIsNeverAcceptedAsYcse() {
        byte[] legacyPayload = Base64.getDecoder().decode(encryptor.encrypt("legacy value"));

        assertThatThrownBy(() -> new EnvelopeCodec().decode(
                legacyPayload, EnvelopeCodec.Target.DATABASE_FIELD))
                .isExactlyInstanceOf(ProtectionFailure.class)
                .hasMessage(ProtectionFailure.SANITIZED_MESSAGE);
    }
}
