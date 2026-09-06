package com.ycsopen.sms.core.common.security.persistence;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Pattern;

/** Versioned locator and row-binding contract shared by current writes and migration validation. */
public final class MessageTaskRowBinding {

    public static final String CURRENT_LOCATOR_PREFIX = "p3c1_";

    private static final int LOCATOR_ENTROPY_BYTES = 32;
    private static final Pattern CURRENT_LOCATOR = Pattern.compile(
            Pattern.quote(CURRENT_LOCATOR_PREFIX) + "[A-Za-z0-9_-]{43}");
    private static final byte[] ROW_BINDING_DOMAIN =
            "YCS-BLIND-ROW-BINDING/v1\0".getBytes(StandardCharsets.US_ASCII);

    private MessageTaskRowBinding() {
    }

    /** Issues a 256-bit opaque locator whose version marker cannot be a historical SHA-256 value. */
    public static String issueCurrentLocator(SecureRandom random) {
        Objects.requireNonNull(random, "random");
        byte[] entropy = new byte[LOCATOR_ENTROPY_BYTES];
        try {
            random.nextBytes(entropy);
            return CURRENT_LOCATOR_PREFIX
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
        } finally {
            Arrays.fill(entropy, (byte) 0);
        }
    }

    public static boolean isCurrentLocator(String value) {
        return value != null && CURRENT_LOCATOR.matcher(value).matches();
    }

    /** Commits the metadata rows to the exact current message row without retaining plaintext. */
    public static byte[] originalRowDigest(
            long tenantId,
            long rowId,
            String messageId,
            String locator,
            byte[] envelope) {
        if (tenantId <= 0 || rowId <= 0 || !validDatabaseMessageId(messageId)
                || !isCurrentLocator(locator) || envelope == null || envelope.length == 0) {
            throw new IllegalArgumentException("invalid message-task row binding");
        }
        byte[] messageIdBytes = strictUtf8(messageId);
        byte[] locatorBytes = locator.getBytes(StandardCharsets.US_ASCII);
        ByteBuffer binding = ByteBuffer.allocate(
                ROW_BINDING_DOMAIN.length + Long.BYTES + Long.BYTES
                        + Integer.BYTES + messageIdBytes.length
                        + Integer.BYTES + locatorBytes.length
                        + Integer.BYTES + envelope.length);
        binding.put(ROW_BINDING_DOMAIN).putLong(tenantId).putLong(rowId)
                .putInt(messageIdBytes.length).put(messageIdBytes)
                .putInt(locatorBytes.length).put(locatorBytes)
                .putInt(envelope.length).put(envelope);
        try {
            return MessageDigest.getInstance("SHA-256").digest(binding.array());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("required digest is unavailable");
        } finally {
            Arrays.fill(binding.array(), (byte) 0);
        }
    }

    private static boolean validDatabaseMessageId(String value) {
        return value != null && !value.isEmpty()
                && value.codePointCount(0, value.length()) <= 64
                && StandardCharsets.UTF_8.newEncoder().canEncode(value)
                && value.chars().noneMatch(character -> Character.isISOControl(character));
    }

    private static byte[] strictUtf8(String value) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value));
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("invalid message-task row binding", exception);
        }
    }
}
