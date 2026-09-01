package com.ycsopen.sms.core.common.security.key;

import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.envelope.ProtectionContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VersionedBlindIndexTest {

    private static final byte[] TOKEN_SECRET = sequence(32, 1);
    private static final OpaqueTokenDigestPort.Binding CAPABILITY_BINDING =
            new OpaqueTokenDigestPort.Binding("tenant:17", "subject:29", "object:41/read");
    private static final OpaqueTokenDigestPort.Binding UPLOAD_BINDING =
            new OpaqueTokenDigestPort.Binding("tenant-draft:17", "subject:29", "session:41");

    @Test
    void exposesOneCallerVisibleWrapWithoutNonceOrAdmission() throws Exception {
        assertThat(KeyProtectionPort.class.getDeclaredMethod("wrap", byte[].class, byte[].class,
                ProtectionContext.class)).isNotNull();
        assertThat(KeyProtectionPort.class.getMethods())
                .filteredOn(method -> method.getName().equals("wrap"))
                .singleElement()
                .satisfies(method -> assertThat(Arrays.asList(method.getParameterTypes()))
                        .containsExactly(byte[].class, byte[].class, ProtectionContext.class));
        assertThat(Modifier.isPublic(WrapOperationAdmissionPort.class.getModifiers())).isFalse();
        assertThat(Arrays.stream(WrapOperationAdmissionPort.class.getDeclaredMethods())
                .map(method -> method.getName()).toList()).containsExactly("reserve");
    }

    @Test
    void wrapsAfterOneReservationAndUnwrapsOnlyWithExactContext() {
        DeterministicTestKeyAdapter adapter = new DeterministicTestKeyAdapter();
        ProtectionContext context = databaseContext("message-41");
        byte[] dek = sequence(32, 11);
        byte[] authenticatedHeader = new EnvelopeCodec()
                .authenticatedHeader("test-kek-v1", 11, EnvelopeCodec.Target.DATABASE_FIELD);

        WrappedDataKey wrapped = adapter.wrap(dek, authenticatedHeader, context);

        assertThat(adapter.reservedWrapCount()).isOne();
        assertThat(wrapped.keyReference()).isEqualTo("test-kek-v1");
        assertThat(wrapped.wrapNonce()).hasSize(12);
        assertThat(ByteBuffer.wrap(wrapped.wrapNonce()).getLong(4)).isEqualTo(1L);
        assertThat(wrapped.wrappedDek()).hasSize(48);
        assertThat(adapter.unwrap(wrapped, authenticatedHeader, context)).containsExactly(dek);
        assertThatThrownBy(() -> adapter.unwrap(wrapped, authenticatedHeader, databaseContext("message-42")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("test key operation failed");

        byte[] wrongKeyHeader = new EnvelopeCodec()
                .authenticatedHeader("other-kek-v1", 11, EnvelopeCodec.Target.DATABASE_FIELD);
        assertThatThrownBy(() -> adapter.wrap(dek, wrongKeyHeader, context))
                .isInstanceOf(IllegalStateException.class);
        assertThat(adapter.reservedWrapCount()).isOne();
    }

    @Test
    void wrappedResultAndVersionedDigestsDefensivelyCopyBinaryValues() {
        byte[] nonce = sequence(12, 3);
        byte[] encryptedDek = sequence(48, 7);
        WrappedDataKey wrapped = new WrappedDataKey("test-kek-v1", nonce, encryptedDek);
        nonce[0] = 99;
        encryptedDek[0] = 99;
        byte[] returnedNonce = wrapped.wrapNonce();
        returnedNonce[1] = 99;

        assertThat(wrapped.wrapNonce()).containsExactly(sequence(12, 3));
        assertThat(wrapped.wrappedDek()).containsExactly(sequence(48, 7));
        assertThat(wrapped.toString()).doesNotContain("test-kek-v1");

        byte[] digest = sequence(32, 19);
        VersionedTokenDigest tokenDigest = new VersionedTokenDigest(
                OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY, 7, digest);
        digest[0] = 99;
        byte[] returnedDigest = tokenDigest.digest();
        returnedDigest[1] = 99;
        assertThat(tokenDigest.digest()).containsExactly(sequence(32, 19));
        assertThat(tokenDigest.toString()).contains("[redacted]");
    }

    @Test
    void canonicalBlindIndexesAreOrderedUniqueAndExactlyFiftyThreeLowercaseBase32Characters() {
        DeterministicTestKeyAdapter adapter = new DeterministicTestKeyAdapter();
        BlindIndexPort.Context context = mobileContext("MESSAGE_TASK", "tenant:17");

        BlindIndexPort.OrderedIndexes writes = adapter.writeIndexes("13800138000", context);
        BlindIndexPort.OrderedIndexes queries = adapter.queryIndexes("13800138000", context);

        assertThat(writes.values()).extracting(VersionedBlindIndex::keyVersion)
                .containsExactly(1, 2);
        assertThat(queries.values()).extracting(VersionedBlindIndex::keyVersion)
                .containsExactly(1, 2);
        assertThat(writes.values()).extracting(VersionedBlindIndex::canonicalValue)
                .allSatisfy(value -> assertThat(value).matches("[a-z2-7]{53}"));
        assertThat(writes.values().getLast().canonicalValue())
                .isEqualTo("alesvuhbuvlf3pvcduj5ghm4kicrnfqkxbocojpoeq453uxdjsjc6");
        assertThat(writes.values()).doesNotHaveDuplicates();
        assertThat(writes.toString()).doesNotContain(writes.values().getFirst().canonicalValue());
    }

    @Test
    void mobileIndexDomainBindsTargetFieldPurposeAndScope() {
        DeterministicTestKeyAdapter adapter = new DeterministicTestKeyAdapter();
        String baseline = activeIndex(adapter, mobileContext("MESSAGE_TASK", "tenant:17"));

        assertThat(activeIndex(adapter, mobileContext("BLACKLIST_ENTRY", "tenant:17")))
                .isNotEqualTo(baseline);
        assertThat(activeIndex(adapter,
                new BlindIndexPort.Context("MESSAGE_TASK", "recipient-mobile",
                        BlindIndexPort.Purpose.MOBILE_ROUTING, "tenant:17")))
                .isNotEqualTo(baseline);
        assertThat(activeIndex(adapter, mobileContext("MESSAGE_TASK", "tenant:18")))
                .isNotEqualTo(baseline);
        assertThatThrownBy(() -> adapter.queryIndexes("+8613800138000",
                mobileContext("MESSAGE_TASK", "tenant:17")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void tokenIssueUsesOnlyActiveAndActiveOrRetiringStoredVersionsVerify() {
        DeterministicTestKeyAdapter adapter = new DeterministicTestKeyAdapter();
        VersionedTokenDigest issued = adapter.issue(
                OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY, CAPABILITY_BINDING, TOKEN_SECRET);
        VersionedTokenDigest retiring = adapter.tokenDigestForVersion(
                OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY, 1,
                CAPABILITY_BINDING, TOKEN_SECRET);

        assertThat(issued.keyVersion()).isEqualTo(2);
        assertThat(HexFormat.of().formatHex(issued.digest()))
                .isEqualTo("3e12d6d32c48339739caad700103f744aa5cb1effeaabf2e9f3c8f9ffb5ce81b");
        assertThat(adapter.verify(OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY,
                CAPABILITY_BINDING, TOKEN_SECRET, issued)).isTrue();
        assertThat(adapter.verify(OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY,
                CAPABILITY_BINDING, TOKEN_SECRET, retiring)).isTrue();
    }

    @Test
    void tokenVerificationFailsClosedForUnknownRetiredRevokedAndWrongLength() {
        DeterministicTestKeyAdapter adapter = new DeterministicTestKeyAdapter();
        VersionedTokenDigest retired = adapter.tokenDigestForVersion(
                OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY, 3,
                CAPABILITY_BINDING, TOKEN_SECRET);
        VersionedTokenDigest revoked = adapter.tokenDigestForVersion(
                OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY, 4,
                CAPABILITY_BINDING, TOKEN_SECRET);
        VersionedTokenDigest unknown = new VersionedTokenDigest(
                OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY, 99, sequence(32, 4));

        assertThat(adapter.verify(OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY,
                CAPABILITY_BINDING, TOKEN_SECRET, retired)).isFalse();
        assertThat(adapter.verify(OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY,
                CAPABILITY_BINDING, TOKEN_SECRET, revoked)).isFalse();
        assertThat(adapter.verify(OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY,
                CAPABILITY_BINDING, TOKEN_SECRET, unknown)).isFalse();
        assertThat(adapter.verify(OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY,
                CAPABILITY_BINDING, new byte[31], unknown)).isFalse();
        assertThatThrownBy(() -> adapter.issue(OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY,
                CAPABILITY_BINDING, new byte[31])).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void capabilityAndUploadDomainsCannotCrossPurposeOrBinding() {
        DeterministicTestKeyAdapter adapter = new DeterministicTestKeyAdapter();
        VersionedTokenDigest capability = adapter.issue(
                OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY, CAPABILITY_BINDING, TOKEN_SECRET);
        VersionedTokenDigest upload = adapter.issue(
                OpaqueTokenDigestPort.Purpose.REGISTRATION_UPLOAD, UPLOAD_BINDING, TOKEN_SECRET);

        assertThat(capability.digest()).isNotEqualTo(upload.digest());
        assertThat(HexFormat.of().formatHex(upload.digest()))
                .isEqualTo("a28ad654a0c6c839ba2fc141e72c4a593348adda74bfcc461307c0a35187b97a");
        assertThat(adapter.verify(OpaqueTokenDigestPort.Purpose.REGISTRATION_UPLOAD,
                UPLOAD_BINDING, TOKEN_SECRET, capability)).isFalse();
        assertThat(adapter.verify(OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY,
                new OpaqueTokenDigestPort.Binding("tenant:17", "subject:29", "object:42/read"),
                TOKEN_SECRET, capability)).isFalse();
        assertThat(adapter.verify(OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY,
                CAPABILITY_BINDING, sequence(32, 2), capability)).isFalse();
    }

    @Test
    void tokenVerificationDelegatesTheDigestDecisionToConstantTimeComparison() {
        AtomicInteger comparisons = new AtomicInteger();
        DeterministicTestKeyAdapter adapter = new DeterministicTestKeyAdapter((left, right) -> {
            comparisons.incrementAndGet();
            return MessageDigest.isEqual(left, right);
        });
        VersionedTokenDigest stored = adapter.issue(
                OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY, CAPABILITY_BINDING, TOKEN_SECRET);

        assertThat(adapter.verify(OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY,
                CAPABILITY_BINDING, sequence(32, 2), stored)).isFalse();
        assertThat(comparisons).hasValue(1);
    }

    @Test
    void invalidSetsAndVersionsCannotRepresentAmbiguousMetadata() {
        VersionedBlindIndex one = new VersionedBlindIndex(1, sequence(32, 1));
        VersionedBlindIndex duplicate = new VersionedBlindIndex(1, sequence(32, 2));
        VersionedBlindIndex two = new VersionedBlindIndex(2, sequence(32, 3));

        assertThatThrownBy(() -> new BlindIndexPort.OrderedIndexes(List.of(one, duplicate)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BlindIndexPort.OrderedIndexes(List.of(two, one)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VersionedBlindIndex(256, new byte[32]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String activeIndex(DeterministicTestKeyAdapter adapter,
                                      BlindIndexPort.Context context) {
        return adapter.queryIndexes("13800138000", context).values().stream()
                .filter(value -> value.keyVersion() == 2)
                .findFirst()
                .orElseThrow()
                .canonicalValue();
    }

    private static BlindIndexPort.Context mobileContext(String targetType, String scope) {
        return new BlindIndexPort.Context(targetType, "mobile",
                BlindIndexPort.Purpose.MOBILE_ROUTING, scope);
    }

    private static ProtectionContext databaseContext(String resource) {
        return new ProtectionContext(ProtectionContext.Purpose.DATABASE_FIELD,
                "crypto-storage-bootstrap", "message_tasks", "mobile_encrypted",
                "tenant:17", resource);
    }

    private static byte[] sequence(int length, int first) {
        byte[] value = new byte[length];
        for (int index = 0; index < length; index++) {
            value[index] = (byte) (first + index);
        }
        return value;
    }
}
