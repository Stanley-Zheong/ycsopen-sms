package com.ycsopen.sms.core.common.security;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
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
    void nullValues_shouldRemainNull() {
        assertThat(encryptor.encrypt(null)).isNull();
        assertThat(encryptor.decrypt(null)).isNull();
    }

    @Test
    void emptyAndUnicodeValues_shouldRoundTrip() {
        String unicodeValue = "短信安全测试：你好，YCSOpen 👋 café";

        assertThat(encryptor.decrypt(encryptor.encrypt(""))).isEmpty();
        assertThat(encryptor.decrypt(encryptor.encrypt(unicodeValue))).isEqualTo(unicodeValue);
    }

    @Test
    void repeatedEncryption_shouldUseFreshIvsAndRemainDecryptable() {
        String plaintext = "same plaintext";

        String firstCiphertext = encryptor.encrypt(plaintext);
        String secondCiphertext = encryptor.encrypt(plaintext);
        byte[] firstPayload = Base64.getDecoder().decode(firstCiphertext);
        byte[] secondPayload = Base64.getDecoder().decode(secondCiphertext);
        byte[] firstIv = Arrays.copyOf(firstPayload, 12);
        byte[] secondIv = Arrays.copyOf(secondPayload, 12);

        assertThat(firstIv).isNotEqualTo(secondIv);
        assertThat(firstCiphertext).isNotEqualTo(secondCiphertext);
        assertThat(encryptor.decrypt(firstCiphertext)).isEqualTo(plaintext);
        assertThat(encryptor.decrypt(secondCiphertext)).isEqualTo(plaintext);
    }

    @Test
    void tamperedAuthenticationTag_shouldFailDecryption() {
        byte[] payload = Base64.getDecoder().decode(encryptor.encrypt("authenticated value"));
        payload[payload.length - 1] ^= 1;
        String tamperedCiphertext = Base64.getEncoder().encodeToString(payload);

        assertThatThrownBy(() -> encryptor.decrypt(tamperedCiphertext))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void differentValidKey_shouldFailDecryption() {
        String ciphertext = encryptor.encrypt("key-bound value");
        FieldEncryptor differentKeyEncryptor = new FieldEncryptor(DIFFERENT_KEY_BASE64);

        assertThatThrownBy(() -> differentKeyEncryptor.decrypt(ciphertext))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void malformedBase64_shouldFailDecryption() {
        assertThatThrownBy(() -> encryptor.decrypt("not-valid-base64%%%"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void payloadShorterThanIv_shouldFailDecryption() {
        String undersizedPayload = Base64.getEncoder().encodeToString(new byte[11]);

        assertThatThrownBy(() -> encryptor.decrypt(undersizedPayload))
                .isInstanceOf(IllegalStateException.class);
    }
}
