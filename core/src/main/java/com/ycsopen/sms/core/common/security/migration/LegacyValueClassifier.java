package com.ycsopen.sms.core.common.security.migration;

import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataTarget.Kind;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataTarget.LegacyRule;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataTarget.NullPolicy;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

/** Strict, metadata-only classification of one reviewed legacy cell. */
public final class LegacyValueClassifier {

    private static final byte[] MAGIC = {'Y', 'C', 'S', 'E'};
    private static final Pattern LOWERCASE_SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern OPAQUE_OBJECT_ID = Pattern.compile("pobj_v1_[A-Za-z0-9_-]{1,72}");
    private final EnvelopeCodec envelopeCodec;

    public LegacyValueClassifier(EnvelopeCodec envelopeCodec) {
        this.envelopeCodec = Objects.requireNonNull(envelopeCodec, "envelopeCodec");
    }

    public Classification classify(ProtectedDataTarget target, byte[] value) {
        Objects.requireNonNull(target, "target");
        if (value == null) {
            return target.nullPolicy() == NullPolicy.ALLOWED
                    ? Classification.NULL_ALLOWED
                    : Classification.CORRUPT;
        }
        if (value.length == 0 || value.length > target.maximumStoredValueBytes()) {
            return Classification.CORRUPT;
        }

        if (hasMagic(value)) {
            if (target.kind() != Kind.DATABASE_FIELD) {
                return Classification.CORRUPT;
            }
            try {
                envelopeCodec.decode(value, EnvelopeCodec.Target.DATABASE_FIELD);
                return Classification.VALID_ENVELOPE;
            } catch (RuntimeException exception) {
                // YCSE-prefixed bytes never fall back to a legacy rule. This is the corruption fence.
                return Classification.CORRUPT;
            }
        }

        return switch (target.legacyRule()) {
            case UTF8_PLAINTEXT -> classifyUtf8(target, value);
            case LOWERCASE_SHA256_HEX -> classifySha256(value);
            case OPAQUE_OBJECT_ID_OR_HTTPS_URL -> classifyObjectReference(value);
            case NONE -> Classification.AMBIGUOUS;
        };
    }

    private static Classification classifyUtf8(ProtectedDataTarget target, byte[] value) {
        if (value.length > target.sourceBoundBytes()) {
            return Classification.AMBIGUOUS;
        }
        String decoded = strictUtf8(value);
        if (decoded == null || decoded.isEmpty() || hasControlCharacter(decoded)) {
            return Classification.AMBIGUOUS;
        }
        return Classification.APPROVED_LEGACY;
    }

    private static Classification classifySha256(byte[] value) {
        if (value.length != 64) {
            return Classification.AMBIGUOUS;
        }
        String decoded = strictUtf8(value);
        return decoded != null && LOWERCASE_SHA256.matcher(decoded).matches()
                ? Classification.APPROVED_LEGACY
                : Classification.AMBIGUOUS;
    }

    private static Classification classifyObjectReference(byte[] value) {
        String decoded = strictUtf8(value);
        if (decoded == null || decoded.isEmpty()) {
            return Classification.AMBIGUOUS;
        }
        if (OPAQUE_OBJECT_ID.matcher(decoded).matches()) {
            return Classification.APPROVED_LEGACY;
        }
        try {
            URI uri = new URI(decoded);
            if ("https".equals(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getRawUserInfo() == null
                    && uri.getRawQuery() == null
                    && uri.getRawFragment() == null) {
                return Classification.APPROVED_LEGACY;
            }
        } catch (URISyntaxException exception) {
            // The stable classification, not parser detail, crosses this boundary.
        }
        return Classification.AMBIGUOUS;
    }

    private static boolean hasMagic(byte[] value) {
        if (value.length < MAGIC.length) {
            return false;
        }
        for (int index = 0; index < MAGIC.length; index++) {
            if (value[index] != MAGIC[index]) {
                return false;
            }
        }
        return true;
    }

    private static String strictUtf8(byte[] value) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value))
                    .toString();
        } catch (CharacterCodingException exception) {
            return null;
        }
    }

    private static boolean hasControlCharacter(String value) {
        return value.codePoints().anyMatch(codePoint -> Character.isISOControl(codePoint));
    }

    public enum Classification {
        VALID_ENVELOPE,
        APPROVED_LEGACY,
        CORRUPT,
        AMBIGUOUS,
        NULL_ALLOWED
    }
}
