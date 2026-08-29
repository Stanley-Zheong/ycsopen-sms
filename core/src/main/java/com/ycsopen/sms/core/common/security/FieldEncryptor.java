package com.ycsopen.sms.core.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 对 PRD 数据字典中标注 🔒 的字段做应用层字段级加密（AES-256-GCM，信封加密思路，见 PRD 6.2.1）。
 * <p><b>重要</b>：本类演示的是"字段加密应该发生在哪一层"（数据访问层，业务代码不碰密钥），
 * 而非生产级密钥管理——生产环境必须把 {@code ycsopen.security.field-encryption.key-base64}
 * 换成从云 KMS 动态获取的密钥，并支持密钥轮换（PRD 6.2.1 最后一条）。</p>
 */
@Component
public class FieldEncryptor {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public FieldEncryptor(@Value("${ycsopen.security.field-encryption.key-base64}") String keyBase64) {
        byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    /** 明文 -> Base64(iv || ciphertext || tag)，落库时存这个字符串（或转 bytes 存 VARBINARY）。 */
    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(cipherText, 0, out, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("field encryption failed", e);
        }
    }

    public String decrypt(String encoded) {
        if (encoded == null) return null;
        try {
            byte[] in = Base64.getDecoder().decode(encoded);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            System.arraycopy(in, 0, iv, 0, IV_LENGTH_BYTES);
            byte[] cipherText = new byte[in.length - IV_LENGTH_BYTES];
            System.arraycopy(in, IV_LENGTH_BYTES, cipherText, 0, cipherText.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("field decryption failed", e);
        }
    }
}
