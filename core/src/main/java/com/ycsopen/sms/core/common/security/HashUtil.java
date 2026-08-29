package com.ycsopen.sms.core.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 手机号等加密字段需要"能等值查询、不能直接看懂"，单靠 AES 密文做不到（同一明文每次加密的密文
 * 因随机 IV 而不同）。因此额外维护一个确定性哈希列（mobile_hash），仅用于索引/等值比较，
 * 不用于反推明文——业务读取真实号码仍必须走 {@link FieldEncryptor#decrypt}。
 */
public final class HashUtil {
    private HashUtil() {}

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
