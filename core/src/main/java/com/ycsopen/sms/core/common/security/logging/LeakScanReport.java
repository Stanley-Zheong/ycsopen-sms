package com.ycsopen.sms.core.common.security.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Sanitized Phase 03 leak result containing counts, digests and reader identities only. */
public final class LeakScanReport {

    public static final String SCHEMA_VERSION = "phase03-leak-result-v1";
    public static final String PHASE = "03-crypto-storage-bootstrap";
    public static final String CHECK_ID = "phase03-complete-leak-scan";
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern IDENTITY = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String subjectDigest;
    private final String status;
    private final int exitCode;
    private final List<TargetResult> targets;
    private final String resultDigest;

    private LeakScanReport(
            String subjectDigest,
            String status,
            int exitCode,
            List<TargetResult> targets,
            String resultDigest) {
        this.subjectDigest = requireSha256(subjectDigest);
        if (!("PASS".equals(status) && exitCode == 0)
                && !("FAIL".equals(status) && exitCode == 1)) {
            throw invalid();
        }
        this.status = status;
        this.exitCode = exitCode;
        this.targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
        if (this.targets.isEmpty()) {
            throw invalid();
        }
        this.resultDigest = requireSha256(resultDigest);
    }

    static LeakScanReport create(String subjectDigest, List<TargetResult> targets) {
        if (targets == null) {
            throw invalid();
        }
        List<TargetResult> immutable;
        try {
            immutable = List.copyOf(targets);
        } catch (RuntimeException exception) {
            throw invalid();
        }
        validateCompleteTargetUnion(immutable);
        boolean clean = immutable.stream().allMatch(target -> target.prohibitedMatches() == 0);
        String status = clean ? "PASS" : "FAIL";
        int exitCode = clean ? 0 : 1;
        String digest = sha256(canonicalBytes(subjectDigest, status, exitCode, immutable, null));
        return new LeakScanReport(subjectDigest, status, exitCode, immutable, digest);
    }

    private static void validateCompleteTargetUnion(List<TargetResult> targets) {
        List<String> ids = targets.stream().map(TargetResult::id).toList();
        if (!ids.equals(List.of(
                "database-cells", "evidence", "logs", "object-bytes", "reports"))) {
            throw invalid();
        }
        for (TargetResult target : targets) {
            String expectedReader = switch (target.id()) {
                case "evidence", "reports" -> SensitiveDataLeakScanner.ARTIFACT_READER_IDENTITY;
                default -> SensitiveDataLeakScanner.READER_IDENTITY;
            };
            if (!expectedReader.equals(target.readerIdentity())) {
                throw invalid();
            }
        }
    }

    public String subjectDigest() {
        return subjectDigest;
    }

    public String status() {
        return status;
    }

    public int exitCode() {
        return exitCode;
    }

    public List<TargetResult> targets() {
        return targets;
    }

    public String resultDigest() {
        return resultDigest;
    }

    /** Canonical JSON compatible with the independent Ruby evidence validator. */
    public byte[] canonicalBytes() {
        return canonicalBytes(subjectDigest, status, exitCode, targets, resultDigest);
    }

    public boolean digestValid() {
        return resultDigest.equals(
                sha256(canonicalBytes(subjectDigest, status, exitCode, targets, null)));
    }

    private static byte[] canonicalBytes(
            String subjectDigest,
            String status,
            int exitCode,
            List<TargetResult> targets,
            String resultDigest) {
        Map<String, Object> root = new TreeMap<>();
        root.put("check_id", CHECK_ID);
        root.put("exit_code", exitCode);
        root.put("phase", PHASE);
        if (resultDigest != null) {
            root.put("result_digest", resultDigest);
        }
        root.put("schema_version", SCHEMA_VERSION);
        root.put("status", status);
        root.put("subject_digest", subjectDigest);
        List<Map<String, Object>> rows = new ArrayList<>(targets.size());
        for (TargetResult target : targets) {
            Map<String, Object> row = new TreeMap<>();
            row.put("id", target.id());
            row.put("prohibited_matches", target.prohibitedMatches());
            row.put("reader_identity", target.readerIdentity());
            row.put("scanned_items", target.scannedItems());
            row.put("sensitivity_status", target.sensitivityStatus());
            rows.add(row);
        }
        root.put("targets", rows);
        try {
            return JSON.writeValueAsBytes(root);
        } catch (JsonProcessingException exception) {
            throw invalid();
        }
    }

    private static String requireSha256(String value) {
        if (value == null || !SHA256.matcher(value).matches()) {
            throw invalid();
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

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("leak scan report rejected");
    }

    @Override
    public String toString() {
        return "LeakScanReport[status=" + status + ", targets=" + targets.size()
                + ", protected-content=[redacted]]";
    }

    public record TargetResult(
            String id,
            String readerIdentity,
            int scannedItems,
            int prohibitedMatches,
            String sensitivityStatus) {

        public static final String SENSITIVITY_PROVEN = "DETECTED_SEEDED_MUTATION";

        public TargetResult {
            if (id == null || !IDENTITY.matcher(id).matches()
                    || readerIdentity == null || !IDENTITY.matcher(readerIdentity).matches()
                    || scannedItems < 1 || prohibitedMatches < 0
                    || !SENSITIVITY_PROVEN.equals(sensitivityStatus)) {
                throw invalid();
            }
        }

        @Override
        public String toString() {
            return "TargetResult[id=" + id + ", readerIdentity=" + readerIdentity
                    + ", scannedItems=" + scannedItems
                    + ", prohibitedMatches=" + prohibitedMatches + "]";
        }
    }
}
