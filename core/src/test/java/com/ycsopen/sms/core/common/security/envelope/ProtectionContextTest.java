package com.ycsopen.sms.core.common.security.envelope;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProtectionContextTest {

    private static final EnvelopeCodec CODEC = new EnvelopeCodec();

    @Test
    void semanticContextHasExactSchemaAndSixLengthPrefixedFields() {
        ProtectionContext context = databaseContext();

        assertThat(context.canonicalBytes()).containsExactly(concat(
                new byte[]{1},
                field("database-field"),
                field("crypto-storage-bootstrap"),
                field("message_tasks"),
                field("mobile_encrypted"),
                field("tenant:42"),
                field("message_id=msg_01")));
    }

    @Test
    void dataAndWrapAadUseDistinctDomainsAndOnlyWrapBindsTheRotatableKeyReference() {
        CipherEnvelope envelope = envelope();
        ProtectionContext context = databaseContext();

        byte[] dataAad = CODEC.dataAad(envelope, context, EnvelopeCodec.Target.DATABASE_FIELD);
        byte[] wrapAad = CODEC.wrapAad(envelope, context, EnvelopeCodec.Target.DATABASE_FIELD);
        byte[] header = CODEC.authenticatedHeader(envelope, EnvelopeCodec.Target.DATABASE_FIELD);

        assertThat(dataAad).startsWith("YCSE-DATA-AAD\0".getBytes(StandardCharsets.US_ASCII));
        assertThat(wrapAad).startsWith("YCSE-WRAP-AAD\0".getBytes(StandardCharsets.US_ASCII));
        assertThat(dataAad).isNotEqualTo(wrapAad);
        assertThat(wrapAad).containsSubsequence(header);
        assertThat(dataAad).endsWith(context.canonicalBytes());
        assertThat(wrapAad).endsWith(context.canonicalBytes());

        CipherEnvelope rotated = new CipherEnvelope("pkcs11", "field-kek.v2",
                envelope.wrapNonce(), envelope.wrappedDek(), envelope.dataNonce(),
                envelope.ciphertext());
        assertThat(CODEC.dataAad(rotated, context, EnvelopeCodec.Target.DATABASE_FIELD))
                .isEqualTo(dataAad);
        assertThat(CODEC.wrapAad(rotated, context, EnvelopeCodec.Target.DATABASE_FIELD))
                .isNotEqualTo(wrapAad);
    }

    @Test
    void everySemanticFieldIsAuthenticated() {
        ProtectionContext original = databaseContext();
        byte[] originalDataAad = CODEC.dataAad(envelope(), original, EnvelopeCodec.Target.DATABASE_FIELD);
        List<ProtectionContext> mutations = List.of(
                new ProtectionContext(ProtectionContext.Purpose.PROTECTED_OBJECT, original.logicalOwner(),
                        original.logicalClass(), original.contentRole(), original.tenantScope(), original.resourceIdentity()),
                new ProtectionContext(original.purpose(), "another-owner", original.logicalClass(),
                        original.contentRole(), original.tenantScope(), original.resourceIdentity()),
                new ProtectionContext(original.purpose(), original.logicalOwner(), "another-table",
                        original.contentRole(), original.tenantScope(), original.resourceIdentity()),
                new ProtectionContext(original.purpose(), original.logicalOwner(), original.logicalClass(),
                        "another-field", original.tenantScope(), original.resourceIdentity()),
                new ProtectionContext(original.purpose(), original.logicalOwner(), original.logicalClass(),
                        original.contentRole(), "tenant:43", original.resourceIdentity()),
                new ProtectionContext(original.purpose(), original.logicalOwner(), original.logicalClass(),
                        original.contentRole(), original.tenantScope(), "message_id=msg_02"));

        for (ProtectionContext mutation : mutations) {
            EnvelopeCodec.Target target = mutation.purpose() == ProtectionContext.Purpose.PROTECTED_OBJECT
                    ? EnvelopeCodec.Target.BUSINESS_LICENSE : EnvelopeCodec.Target.DATABASE_FIELD;
            assertThat(CODEC.dataAad(envelope(), mutation, target)).isNotEqualTo(originalDataAad);
        }
    }

    @Test
    void exactAllowedPurposesAreAccepted() {
        assertThat(context(ProtectionContext.Purpose.DATABASE_FIELD).canonicalBytes()).isNotEmpty();
        assertThat(context(ProtectionContext.Purpose.PROTECTED_OBJECT).canonicalBytes()).isNotEmpty();
        assertThat(context(ProtectionContext.Purpose.MYSQL_ENCRYPTED_SNAPSHOT_CHUNK).canonicalBytes()).isNotEmpty();
    }

    @Test
    void emptyOversizedNoncanonicalAndInvalidTenantFieldsFailWithOneSanitizedCategory() {
        assertInvalid(() -> new ProtectionContext(ProtectionContext.Purpose.DATABASE_FIELD, "", "table", "field",
                "tenant:1", "row"));
        assertInvalid(() -> new ProtectionContext(ProtectionContext.Purpose.DATABASE_FIELD, "a".repeat(1025), "table",
                "field", "tenant:1", "row"));
        ProtectionContext maximumFields = new ProtectionContext(ProtectionContext.Purpose.DATABASE_FIELD,
                "a".repeat(1024), "b".repeat(1024), "c".repeat(1024),
                "tenant:" + "d".repeat(1017), "e".repeat(1024));
        assertThat(maximumFields.canonicalBytes()).hasSizeLessThanOrEqualTo(ProtectionContext.MAXIMUM_CANONICAL_BYTES);
        assertInvalid(() -> new ProtectionContext(ProtectionContext.Purpose.DATABASE_FIELD, "owner", "table", "field",
                "tenant:", "row"));
        assertInvalid(() -> new ProtectionContext(ProtectionContext.Purpose.DATABASE_FIELD, "owner\ud800", "table", "field",
                "tenant:1", "row"));
    }

    @Test
    void purposeMustMatchTheSelectedCapacityTarget() {
        assertInvalid(() -> CODEC.dataAad(envelope(), context(ProtectionContext.Purpose.PROTECTED_OBJECT),
                EnvelopeCodec.Target.DATABASE_FIELD));
        assertInvalid(() -> CODEC.wrapAad(envelope(), context(ProtectionContext.Purpose.DATABASE_FIELD),
                EnvelopeCodec.Target.BUSINESS_LICENSE));
    }

    @Test
    void canonicalOutputIsDefensivelyCopied() {
        ProtectionContext context = databaseContext();
        byte[] first = context.canonicalBytes();
        first[0] = 99;

        assertThat(context.canonicalBytes()[0]).isEqualTo((byte) 1);
    }

    private static ProtectionContext databaseContext() {
        return new ProtectionContext(ProtectionContext.Purpose.DATABASE_FIELD,
                "crypto-storage-bootstrap", "message_tasks", "mobile_encrypted",
                "tenant:42", "message_id=msg_01");
    }

    private static ProtectionContext context(ProtectionContext.Purpose purpose) {
        return new ProtectionContext(purpose, "crypto-storage-bootstrap", "logical-class", "content-role",
                "global", "resource-01");
    }

    private static CipherEnvelope envelope() {
        return new CipherEnvelope("pkcs11", "kek.v1", new byte[12], new byte[48], new byte[12], new byte[17]);
    }

    private static byte[] field(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return concat(new byte[]{(byte) (bytes.length >>> 8), (byte) bytes.length}, bytes);
    }

    private static byte[] concat(byte[]... parts) {
        int length = Arrays.stream(parts).mapToInt(part -> part.length).sum();
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
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
        void run();
    }
}
