package com.ycsopen.sms.core.common.security.logging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.common.security.logging.SensitiveDataLeakScanner.CanaryKind;
import com.ycsopen.sms.core.common.security.logging.SensitiveDataLeakScanner.RuntimeScan;
import com.ycsopen.sms.core.common.security.logging.SensitiveDataLeakScanner.ScanSession;
import com.ycsopen.sms.core.common.security.logging.SensitiveDataLeakScanner.Surface;
import com.ycsopen.sms.core.common.security.logging.SensitiveDataLeakScanner.SurfaceItem;
import com.ycsopen.sms.core.common.security.logging.SensitiveDataLeakScanner.SurfaceTarget;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SensitiveDataLeakScannerTest {

    private static final String SUBJECT_DIGEST = "a".repeat(64);
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void cleanExactSurfaceUnionProducesOnlySanitizedCountsDigestsAndIdentities() throws Exception {
        SensitiveDataLeakScanner scanner = new SensitiveDataLeakScanner();
        ScanSession session = scanner.begin(new FixedRandom());
        Map<CanaryKind, String> hashes = session.canaries().hashes();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int exit = new Phase03LeakScanCommand().execute(
                session, SUBJECT_DIGEST, cleanSurfaces(), artifactReport(), output);
        String encoded = output.toString(StandardCharsets.UTF_8).stripTrailing();
        JsonNode json = JSON.readTree(encoded);

        assertThat(exit).isZero();
        assertThat(json.get("status").textValue()).isEqualTo("PASS");
        assertThat(json.get("exit_code").intValue()).isZero();
        assertThat(json.get("targets")).extracting(target -> target.get("id").textValue())
                .containsExactly("database-cells", "evidence", "logs", "object-bytes", "reports");
        assertThat(json.get("targets"))
                .extracting(target -> target.get("reader_identity").textValue())
                .containsExactly(
                        SensitiveDataLeakScanner.READER_IDENTITY,
                        SensitiveDataLeakScanner.ARTIFACT_READER_IDENTITY,
                        SensitiveDataLeakScanner.READER_IDENTITY,
                        SensitiveDataLeakScanner.READER_IDENTITY,
                        SensitiveDataLeakScanner.ARTIFACT_READER_IDENTITY);
        assertThat(json.get("targets"))
                .allSatisfy(target -> {
                    assertThat(target.get("scanned_items").intValue()).isOne();
                    assertThat(target.get("prohibited_matches").intValue()).isZero();
                    assertThat(target.get("sensitivity_status").textValue())
                            .isEqualTo(LeakScanReport.TargetResult.SENSITIVITY_PROVEN);
                });
        assertThat(json.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "schema_version", "phase", "check_id", "subject_digest", "status",
                "exit_code", "targets", "result_digest");
        session.canaries().hashes().forEach((kind, digest) -> {
            assertThat(digest).matches("[0-9a-f]{64}");
            assertThat(encoded).doesNotContain(session.canaries().value(kind));
        });
        assertThat(session.canaries().toString())
                .doesNotContain(session.canaries().value(CanaryKind.CREDENTIAL));
        assertThat(hashes).hasSize(CanaryKind.values().length);
    }

    @Test
    void canarySessionsRemainUniqueInsideOneProcessEvenWithRepeatedEntropy() {
        SensitiveDataLeakScanner scanner = new SensitiveDataLeakScanner();
        ScanSession first = scanner.begin(new FixedRandom());
        ScanSession second = scanner.begin(new FixedRandom());

        assertThat(first.canaries().hashes()).isNotEqualTo(second.canaries().hashes());
        for (CanaryKind kind : CanaryKind.values()) {
            assertThat(first.canaries().value(kind))
                    .isNotEqualTo(second.canaries().value(kind));
        }
    }

    @Test
    void detectsDirectBase64HexUrlEncodedAndReaderSplitCanariesWithoutEchoingThem() {
        assertDetected(CanaryKind.CAPABILITY, value -> value.getBytes(StandardCharsets.UTF_8));
        assertDetected(CanaryKind.CREDENTIAL,
                value -> Base64.getEncoder().encode(value.getBytes(StandardCharsets.UTF_8)));
        assertDetected(CanaryKind.URL,
                value -> Base64.getUrlEncoder().withoutPadding()
                        .encode(value.getBytes(StandardCharsets.UTF_8)));
        assertDetected(CanaryKind.OBJECT,
                value -> HexFormat.of().formatHex(value.getBytes(StandardCharsets.UTF_8))
                        .getBytes(StandardCharsets.US_ASCII));
        assertDetected(CanaryKind.CRLF,
                value -> URLEncoder.encode(value, StandardCharsets.UTF_8)
                        .getBytes(StandardCharsets.US_ASCII));

        SensitiveDataLeakScanner scanner = new SensitiveDataLeakScanner();
        ScanSession split = scanner.begin(new FixedRandom());
        byte[] marker = split.canaries().value(CanaryKind.DEK)
                .getBytes(StandardCharsets.UTF_8);
        byte[] prefixed = new byte[8_189 + marker.length];
        java.util.Arrays.fill(prefixed, 0, 8_189, (byte) 'x');
        System.arraycopy(marker, 0, prefixed, 8_189, marker.length);
        List<Surface> surfaces = replace(
                cleanSurfaces(), SurfaceTarget.LOGS,
                new Surface(SurfaceTarget.LOGS, SensitiveDataLeakScanner.READER_IDENTITY,
                        List.of(new SurfaceItem("split-reader",
                                () -> new FragmentedInputStream(prefixed, 3)))));

        RuntimeScan report = split.scanRuntime(SUBJECT_DIGEST, surfaces);

        assertThat(target(report.targets(), SurfaceTarget.LOGS).prohibitedMatches()).isPositive();
        assertThat(report.toString())
                .doesNotContain(split.canaries().value(CanaryKind.DEK));
    }

    @Test
    void missingDuplicateWrongReaderAndEmptyTargetsFailClosed() {
        SensitiveDataLeakScanner scanner = new SensitiveDataLeakScanner();
        List<Surface> missing = new ArrayList<>(cleanSurfaces());
        missing.removeLast();
        assertRejected(() -> scanner.begin(new FixedRandom()).scanRuntime(SUBJECT_DIGEST, missing));

        List<Surface> duplicate = new ArrayList<>(cleanSurfaces());
        duplicate.set(duplicate.size() - 1, duplicate.getFirst());
        assertRejected(() -> scanner.begin(new FixedRandom()).scanRuntime(SUBJECT_DIGEST, duplicate));

        assertRejected(() -> new Surface(
                SurfaceTarget.EVIDENCE, SensitiveDataLeakScanner.READER_IDENTITY,
                List.of(SurfaceItem.bytes("evidence", new byte[]{1}))));
        assertRejected(() -> new Surface(
                SurfaceTarget.LOGS, SensitiveDataLeakScanner.ARTIFACT_READER_IDENTITY,
                List.of(SurfaceItem.bytes("logs", new byte[]{1}))));

        List<Surface> empty = replace(
                cleanSurfaces(), SurfaceTarget.LOGS,
                new Surface(SurfaceTarget.LOGS,
                        SensitiveDataLeakScanner.READER_IDENTITY, List.of()));
        assertRejected(() -> scanner.begin(new FixedRandom()).scanRuntime(SUBJECT_DIGEST, empty));
    }

    @Test
    void duplicateItemsReadFailuresAndItemSizeEscapeFailClosed() {
        SensitiveDataLeakScanner scanner = new SensitiveDataLeakScanner();
        SurfaceItem duplicate = SurfaceItem.bytes("same", new byte[]{1});
        List<Surface> duplicateItems = replace(
                cleanSurfaces(), SurfaceTarget.LOGS,
                new Surface(SurfaceTarget.LOGS, SensitiveDataLeakScanner.READER_IDENTITY,
                        List.of(duplicate, duplicate)));
        assertRejected(() -> scanner.begin(new FixedRandom())
                .scanRuntime(SUBJECT_DIGEST, duplicateItems));

        List<Surface> unreadable = replace(
                cleanSurfaces(), SurfaceTarget.LOGS,
                new Surface(SurfaceTarget.LOGS, SensitiveDataLeakScanner.READER_IDENTITY,
                        List.of(new SurfaceItem("broken", () -> {
                            throw new IOException("canary must not escape");
                        }))));
        assertRejected(() -> scanner.begin(new FixedRandom()).scanRuntime(SUBJECT_DIGEST, unreadable));

        List<Surface> oversized = replace(
                cleanSurfaces(), SurfaceTarget.OBJECT_BYTES,
                new Surface(SurfaceTarget.OBJECT_BYTES, SensitiveDataLeakScanner.READER_IDENTITY,
                        List.of(new SurfaceItem("oversized",
                                () -> new RepeatingInputStream(
                                        SensitiveDataLeakScanner.MAXIMUM_ITEM_BYTES + 1)))));
        assertRejected(() -> scanner.begin(new FixedRandom()).scanRuntime(SUBJECT_DIGEST, oversized));
    }

    @Test
    void subjectBindingAndSessionAreOneShot() {
        SensitiveDataLeakScanner scanner = new SensitiveDataLeakScanner();
        assertRejected(() -> scanner.begin(new FixedRandom()).scanRuntime("0", cleanSurfaces()));

        ScanSession session = scanner.begin(new FixedRandom());
        assertThat(session.scanRuntime(SUBJECT_DIGEST, cleanSurfaces()).targets()).hasSize(3);
        assertRejected(() -> session.scanRuntime(SUBJECT_DIGEST, cleanSurfaces()));
    }

    @Test
    void commandWritesCanonicalSanitizedReportAndReturnsLeakStatus() {
        SensitiveDataLeakScanner scanner = new SensitiveDataLeakScanner();
        ScanSession session = scanner.begin(new FixedRandom());
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        int exit = new Phase03LeakScanCommand().execute(
                session, SUBJECT_DIGEST, cleanSurfaces(), artifactReport(), output);

        assertThat(exit).isZero();
        assertThat(output.toString(StandardCharsets.UTF_8)).endsWith("\n");
        assertThat(output.toString(StandardCharsets.UTF_8))
                .doesNotContain(session.canaries().value(CanaryKind.CREDENTIAL));
    }

    @Test
    void commandRejectsOmittedForgedLeakingAndDigestDriftArtifactResults() {
        byte[] wrongReader = artifactReport(
                SensitiveDataLeakScanner.READER_IDENTITY, 0, true);
        byte[] leaking = artifactReport(
                SensitiveDataLeakScanner.ARTIFACT_READER_IDENTITY, 1, true);
        byte[] missing = artifactReport(
                SensitiveDataLeakScanner.ARTIFACT_READER_IDENTITY, 0, false);
        byte[] digestDrift = artifactReport();
        digestDrift[digestDrift.length - 2] ^= 1;

        for (byte[] invalid : List.of(wrongReader, leaking, missing, digestDrift)) {
            assertRejected(() -> new Phase03LeakScanCommand().execute(
                    new SensitiveDataLeakScanner().begin(new FixedRandom()),
                    SUBJECT_DIGEST, cleanSurfaces(), invalid, new ByteArrayOutputStream()));
        }
    }

    private static void assertDetected(CanaryKind kind, Encoder encoder) {
        SensitiveDataLeakScanner scanner = new SensitiveDataLeakScanner();
        ScanSession session = scanner.begin(new FixedRandom());
        byte[] encoded = encoder.encode(session.canaries().value(kind));
        List<Surface> surfaces = replace(
                cleanSurfaces(), SurfaceTarget.DATABASE_CELLS,
                new Surface(SurfaceTarget.DATABASE_CELLS,
                        SensitiveDataLeakScanner.READER_IDENTITY,
                        List.of(SurfaceItem.bytes("seeded-database", encoded))));

        RuntimeScan report = session.scanRuntime(SUBJECT_DIGEST, surfaces);

        assertThat(target(report.targets(), SurfaceTarget.DATABASE_CELLS).prohibitedMatches())
                .isPositive();
        assertThat(report.toString())
                .doesNotContain(session.canaries().value(kind));
    }

    private static LeakScanReport.TargetResult target(
            List<LeakScanReport.TargetResult> targets, SurfaceTarget target) {
        return targets.stream()
                .filter(result -> result.id().equals(target.id()))
                .findFirst().orElseThrow();
    }

    private static List<Surface> cleanSurfaces() {
        List<Surface> surfaces = new ArrayList<>();
        for (SurfaceTarget target : List.of(
                SurfaceTarget.DATABASE_CELLS, SurfaceTarget.LOGS, SurfaceTarget.OBJECT_BYTES)) {
            surfaces.add(new Surface(
                    target, target.readerIdentity(),
                    List.of(SurfaceItem.bytes(
                            target.id() + "-clean", ("safe-" + target.id())
                                    .getBytes(StandardCharsets.UTF_8)))));
        }
        return surfaces;
    }

    private static byte[] artifactReport() {
        return artifactReport(
                SensitiveDataLeakScanner.ARTIFACT_READER_IDENTITY, 0, true);
    }

    private static byte[] artifactReport(
            String evidenceReader, int evidenceMatches, boolean includeReports) {
        Map<String, Object> root = new TreeMap<>();
        root.put("check_id", "phase03-artifact-leak-scan");
        root.put("exit_code", 0);
        root.put("input_digest", "b".repeat(64));
        root.put("phase", LeakScanReport.PHASE);
        root.put("schema_version", "phase03-artifact-leak-scan-v1");
        root.put("status", "PASS");
        List<Map<String, Object>> targets = new ArrayList<>();
        targets.add(artifactTarget("evidence", evidenceReader, evidenceMatches));
        if (includeReports) {
            targets.add(artifactTarget(
                    "reports", SensitiveDataLeakScanner.ARTIFACT_READER_IDENTITY, 0));
        }
        root.put("targets", targets);
        try {
            root.put("result_digest", sha256(JSON.writeValueAsBytes(root)));
            return JSON.writeValueAsBytes(root);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Map<String, Object> artifactTarget(
            String id, String readerIdentity, int prohibitedMatches) {
        Map<String, Object> target = new TreeMap<>();
        target.put("id", id);
        target.put("prohibited_matches", prohibitedMatches);
        target.put("reader_identity", readerIdentity);
        target.put("scanned_items", 1);
        target.put("sensitivity_status", LeakScanReport.TargetResult.SENSITIVITY_PROVEN);
        return target;
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static List<Surface> replace(
            List<Surface> source, SurfaceTarget target, Surface replacement) {
        List<Surface> output = new ArrayList<>(source);
        for (int index = 0; index < output.size(); index++) {
            if (output.get(index).target() == target) {
                output.set(index, replacement);
                return output;
            }
        }
        throw new IllegalArgumentException("target missing");
    }

    private static void assertRejected(ThrowingCall call) {
        assertThatThrownBy(call::run)
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("sensitive data leak scan rejected")
                .hasNoCause();
    }

    @FunctionalInterface
    private interface Encoder {
        byte[] encode(String value);
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }

    private static final class FixedRandom extends SecureRandom {
        @Override
        public void nextBytes(byte[] bytes) {
            for (int index = 0; index < bytes.length; index++) {
                bytes[index] = (byte) (index + 1);
            }
        }
    }

    private static final class FragmentedInputStream extends InputStream {
        private final ByteArrayInputStream delegate;
        private final int fragment;

        private FragmentedInputStream(byte[] value, int fragment) {
            delegate = new ByteArrayInputStream(value);
            this.fragment = fragment;
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(byte[] output, int offset, int length) {
            return delegate.read(output, offset, Math.min(fragment, length));
        }
    }

    private static final class RepeatingInputStream extends InputStream {
        private long remaining;

        private RepeatingInputStream(long remaining) {
            this.remaining = remaining;
        }

        @Override
        public int read() {
            if (remaining == 0) {
                return -1;
            }
            remaining--;
            return 'x';
        }

        @Override
        public int read(byte[] output, int offset, int length) {
            if (remaining == 0) {
                return -1;
            }
            int count = Math.toIntExact(Math.min(remaining, length));
            java.util.Arrays.fill(output, offset, offset + count, (byte) 'x');
            remaining -= count;
            return count;
        }
    }
}
