package com.ycsopen.sms.core.cmpp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/** CMPP CONNECT authenticator: MD5(source_addr + 9 zero bytes + password + timestamp). */
public final class CmppAuthenticator {
    private CmppAuthenticator() {
    }

    public static byte[] authenticatorSource(String sourceAddress, String password, int timestamp) {
        if (blank(sourceAddress) || blank(password) || timestamp <= 0) {
            throw new IllegalArgumentException("invalid CMPP auth input");
        }
        byte[] source = sourceAddress.trim().getBytes(StandardCharsets.US_ASCII);
        byte[] secret = password.getBytes(StandardCharsets.US_ASCII);
        byte[] time = String.format("%010d", timestamp).getBytes(StandardCharsets.US_ASCII);
        byte[] input = new byte[source.length + 9 + secret.length + time.length];
        try {
            int offset = 0;
            System.arraycopy(source, 0, input, offset, source.length);
            offset += source.length + 9;
            System.arraycopy(secret, 0, input, offset, secret.length);
            offset += secret.length;
            System.arraycopy(time, 0, input, offset, time.length);
            return MessageDigest.getInstance("MD5").digest(input);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("MD5 unavailable", impossible);
        } finally {
            Arrays.fill(secret, (byte) 0);
            Arrays.fill(input, (byte) 0);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
