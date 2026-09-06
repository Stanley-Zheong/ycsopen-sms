package com.ycsopen.sms.core.common.security.envelope;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Canonical six-field semantic context authenticated by both YCSE/v1 AEAD operations. */
public final class ProtectionContext {

    public static final int AAD_SCHEMA = 1;
    public static final int MAXIMUM_FIELD_BYTES = 1_024;
    public static final int MAXIMUM_CANONICAL_BYTES = 6_147;
    private static final int FIELD_COUNT = 6;

    public enum Purpose {
        DATABASE_FIELD("database-field"),
        PROTECTED_OBJECT("protected-object"),
        MYSQL_ENCRYPTED_SNAPSHOT_CHUNK("mysql-encrypted-snapshot-chunk");

        private final String wireValue;

        Purpose(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }
    }

    private final Purpose purpose;
    private final String logicalOwner;
    private final String logicalClass;
    private final String contentRole;
    private final String tenantScope;
    private final String resourceIdentity;
    private final byte[] canonicalBytes;

    public ProtectionContext(Purpose purpose,
                             String logicalOwner,
                             String logicalClass,
                             String contentRole,
                             String tenantScope,
                             String resourceIdentity) {
        this.purpose = requirePurpose(purpose);
        this.logicalOwner = requireText(logicalOwner);
        this.logicalClass = requireText(logicalClass);
        this.contentRole = requireText(contentRole);
        this.tenantScope = requireTenantScope(tenantScope);
        this.resourceIdentity = requireText(resourceIdentity);
        this.canonicalBytes = encode();
    }

    public Purpose purpose() {
        return purpose;
    }

    public String logicalOwner() {
        return logicalOwner;
    }

    public String logicalClass() {
        return logicalClass;
    }

    public String contentRole() {
        return contentRole;
    }

    public String tenantScope() {
        return tenantScope;
    }

    public String resourceIdentity() {
        return resourceIdentity;
    }

    public byte[] canonicalBytes() {
        return canonicalBytes.clone();
    }

    private byte[] encode() {
        byte[][] fields = {
                utf8(purpose.wireValue), utf8(logicalOwner), utf8(logicalClass),
                utf8(contentRole), utf8(tenantScope), utf8(resourceIdentity)
        };
        long encodedLength = 1;
        for (byte[] field : fields) {
            encodedLength = checkedAdd(encodedLength, 2L);
            encodedLength = checkedAdd(encodedLength, field.length);
        }
        if (encodedLength > MAXIMUM_CANONICAL_BYTES) {
            throw ProtectionFailure.invalid();
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream((int) encodedLength);
        output.write(AAD_SCHEMA);
        for (byte[] field : fields) {
            output.write(field.length >>> 8);
            output.write(field.length);
            output.writeBytes(field);
        }
        return output.toByteArray();
    }

    private static Purpose requirePurpose(Purpose purpose) {
        if (purpose == null) {
            throw ProtectionFailure.invalid();
        }
        return purpose;
    }

    private static String requireTenantScope(String value) {
        String checked = requireText(value);
        if (!checked.equals("global") && !(checked.startsWith("tenant:") && checked.length() > "tenant:".length())) {
            throw ProtectionFailure.invalid();
        }
        return checked;
    }

    private static String requireText(String value) {
        if (value == null || value.isEmpty()) {
            throw ProtectionFailure.invalid();
        }
        byte[] bytes = utf8(value);
        if (bytes.length > MAXIMUM_FIELD_BYTES) {
            throw ProtectionFailure.invalid();
        }
        return value;
    }

    private static byte[] utf8(String value) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value));
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException exception) {
            throw ProtectionFailure.invalid();
        }
    }

    private static long checkedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw ProtectionFailure.invalid();
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProtectionContext that)) {
            return false;
        }
        return purpose == that.purpose
                && logicalOwner.equals(that.logicalOwner)
                && logicalClass.equals(that.logicalClass)
                && contentRole.equals(that.contentRole)
                && tenantScope.equals(that.tenantScope)
                && resourceIdentity.equals(that.resourceIdentity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(purpose, logicalOwner, logicalClass, contentRole, tenantScope, resourceIdentity);
    }

    @Override
    public String toString() {
        return "ProtectionContext[purpose=" + purpose.wireValue + ", values=[redacted]]";
    }
}
