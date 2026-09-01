package com.ycsopen.sms.core.common.security.logging;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ycsopen.sms.core.common.security.logging.LeakScanReport.TargetResult;
import com.ycsopen.sms.core.common.security.logging.SensitiveDataLeakScanner.RuntimeScan;
import com.ycsopen.sms.core.common.security.logging.SensitiveDataLeakScanner.ScanSession;
import com.ycsopen.sms.core.common.security.logging.SensitiveDataLeakScanner.Surface;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Combines the three Java real-reader results with the independently executed Ruby artifact
 * scanner's evidence/report results. Neither scanner may claim the other's reader identity.
 */
public final class Phase03LeakScanCommand {

    private static final String ARTIFACT_SCHEMA = "phase03-artifact-leak-scan-v1";
    private static final String ARTIFACT_CHECK = "phase03-artifact-leak-scan";
    private static final int ARTIFACT_REPORT_LIMIT = 65_536;
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> ARTIFACT_FIELDS = Set.of(
            "schema_version", "phase", "check_id", "status", "exit_code",
            "input_digest", "targets", "result_digest");
    private static final Set<String> TARGET_FIELDS = Set.of(
            "id", "reader_identity", "scanned_items", "prohibited_matches",
            "sensitivity_status");
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build());

    public int execute(
            ScanSession session,
            String independentlyComputedSubjectDigest,
            List<Surface> runtimeSurfaces,
            byte[] artifactReportBytes,
            OutputStream output) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(output, "output");
        RuntimeScan runtime = session.scanRuntime(
                independentlyComputedSubjectDigest, runtimeSurfaces);
        List<TargetResult> combined = new ArrayList<>(runtime.targets());
        combined.addAll(parseArtifactReport(artifactReportBytes));
        combined.sort(Comparator.comparing(TargetResult::id));
        LeakScanReport report = LeakScanReport.create(runtime.subjectDigest(), combined);
        byte[] encoded = report.canonicalBytes();
        try {
            output.write(encoded);
            output.write('\n');
            output.flush();
            return report.exitCode();
        } catch (IOException exception) {
            throw rejected();
        } finally {
            Arrays.fill(encoded, (byte) 0);
        }
    }

    private static List<TargetResult> parseArtifactReport(byte[] input) {
        if (input == null || input.length < 1 || input.length > ARTIFACT_REPORT_LIMIT) {
            throw rejected();
        }
        try {
            JsonNode root = JSON.readTree(input);
            if (root == null || !root.isObject() || !fields(root).equals(ARTIFACT_FIELDS)
                    || !ARTIFACT_SCHEMA.equals(text(root, "schema_version"))
                    || !LeakScanReport.PHASE.equals(text(root, "phase"))
                    || !ARTIFACT_CHECK.equals(text(root, "check_id"))
                    || !"PASS".equals(text(root, "status"))
                    || integer(root, "exit_code") != 0
                    || !SHA256.matcher(text(root, "input_digest")).matches()
                    || !SHA256.matcher(text(root, "result_digest")).matches()
                    || !text(root, "result_digest").equals(artifactDigest(root))) {
                throw rejected();
            }
            JsonNode targets = root.get("targets");
            if (targets == null || !targets.isArray() || targets.size() != 2) {
                throw rejected();
            }
            List<TargetResult> result = new ArrayList<>(2);
            for (JsonNode target : targets) {
                if (!fields(target).equals(TARGET_FIELDS)) {
                    throw rejected();
                }
                result.add(new TargetResult(
                        text(target, "id"), text(target, "reader_identity"),
                        positiveInteger(target, "scanned_items"),
                        integer(target, "prohibited_matches"),
                        text(target, "sensitivity_status")));
            }
            result.sort(Comparator.comparing(TargetResult::id));
            if (!result.stream().map(TargetResult::id).toList()
                    .equals(List.of("evidence", "reports"))
                    || result.stream().anyMatch(target ->
                    !SensitiveDataLeakScanner.ARTIFACT_READER_IDENTITY
                            .equals(target.readerIdentity())
                            || target.prohibitedMatches() != 0
                            || !TargetResult.SENSITIVITY_PROVEN
                            .equals(target.sensitivityStatus()))) {
                throw rejected();
            }
            return List.copyOf(result);
        } catch (RuntimeException | IOException exception) {
            throw rejected();
        }
    }

    private static String artifactDigest(JsonNode root) {
        ObjectNode withoutDigest = ((ObjectNode) root).deepCopy();
        withoutDigest.remove("result_digest");
        return sha256(canonicalBytes(withoutDigest));
    }

    private static byte[] canonicalBytes(JsonNode node) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeCanonical(node, output);
        return output.toByteArray();
    }

    private static void writeCanonical(JsonNode node, ByteArrayOutputStream output) {
        try {
            if (node.isObject()) {
                output.write('{');
                List<String> names = new ArrayList<>();
                node.fieldNames().forEachRemaining(names::add);
                names.sort(Comparator.naturalOrder());
                for (int index = 0; index < names.size(); index++) {
                    if (index > 0) {
                        output.write(',');
                    }
                    output.writeBytes(JSON.writeValueAsBytes(names.get(index)));
                    output.write(':');
                    writeCanonical(node.get(names.get(index)), output);
                }
                output.write('}');
            } else if (node.isArray()) {
                output.write('[');
                for (int index = 0; index < node.size(); index++) {
                    if (index > 0) {
                        output.write(',');
                    }
                    writeCanonical(node.get(index), output);
                }
                output.write(']');
            } else if (node.isTextual()) {
                output.writeBytes(JSON.writeValueAsBytes(node.textValue()));
            } else if (node.isBoolean()) {
                output.writeBytes(Boolean.toString(node.booleanValue())
                        .getBytes(StandardCharsets.US_ASCII));
            } else if (node.isIntegralNumber()) {
                output.writeBytes(node.bigIntegerValue().toString()
                        .getBytes(StandardCharsets.US_ASCII));
            } else {
                throw rejected();
            }
        } catch (IOException exception) {
            throw rejected();
        }
    }

    private static Set<String> fields(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Set.of();
        }
        Set<String> fields = new java.util.HashSet<>();
        node.fieldNames().forEachRemaining(fields::add);
        return Set.copyOf(fields);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isEmpty()) {
            throw rejected();
        }
        return value.textValue();
    }

    private static int integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()
                || value.intValue() < 0) {
            throw rejected();
        }
        return value.intValue();
    }

    private static int positiveInteger(JsonNode node, String field) {
        int value = integer(node, field);
        if (value < 1) {
            throw rejected();
        }
        return value;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Java 21 must provide SHA-256", exception);
        }
    }

    private static IllegalStateException rejected() {
        return new IllegalStateException("sensitive data leak scan rejected");
    }
}
