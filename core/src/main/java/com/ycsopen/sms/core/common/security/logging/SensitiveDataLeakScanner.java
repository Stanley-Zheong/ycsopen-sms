package com.ycsopen.sms.core.common.security.logging;

import com.ycsopen.sms.core.common.security.logging.LeakScanReport.TargetResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Bounded multi-surface scanner for run-owned synthetic canaries. Canary values are created only
 * inside a scan session and never enter its report or exception text.
 */
public final class SensitiveDataLeakScanner {

    public static final String READER_IDENTITY = "phase03-leak-scanner";
    public static final String ARTIFACT_READER_IDENTITY = "phase03-artifact-scanner";
    public static final long MAXIMUM_ITEM_BYTES = 16L * 1024 * 1024;
    public static final long MAXIMUM_TOTAL_BYTES = 128L * 1024 * 1024;
    public static final int MAXIMUM_ITEMS_PER_TARGET = 4_096;
    private static final int READ_BUFFER_BYTES = 8_192;
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9][a-z0-9._:-]{0,191}");
    private static final AtomicLong SESSION_SEQUENCE = new AtomicLong();
    private static final Set<SurfaceTarget> RUNTIME_TARGETS = Set.of(
            SurfaceTarget.DATABASE_CELLS, SurfaceTarget.LOGS, SurfaceTarget.OBJECT_BYTES);

    /** Exact Phase 03 surface union. Metadata is read with its owning DB/object reader. */
    public enum SurfaceTarget {
        DATABASE_CELLS("database-cells", READER_IDENTITY),
        EVIDENCE("evidence", ARTIFACT_READER_IDENTITY),
        LOGS("logs", READER_IDENTITY),
        OBJECT_BYTES("object-bytes", READER_IDENTITY),
        REPORTS("reports", ARTIFACT_READER_IDENTITY);

        private final String id;
        private final String readerIdentity;

        SurfaceTarget(String id, String readerIdentity) {
            this.id = id;
            this.readerIdentity = readerIdentity;
        }

        public String id() {
            return id;
        }

        public String readerIdentity() {
            return readerIdentity;
        }
    }

    public enum CanaryKind {
        PHONE,
        IDENTITY,
        CREDENTIAL,
        OBJECT,
        DEK,
        CAPABILITY,
        URL,
        CRLF
    }

    public ScanSession begin() {
        return begin(new SecureRandom());
    }

    ScanSession begin(SecureRandom random) {
        Objects.requireNonNull(random, "random");
        byte[] entropy = new byte[18];
        random.nextBytes(entropy);
        String nonce = HexFormat.of().formatHex(entropy)
                + Long.toUnsignedString(SESSION_SEQUENCE.incrementAndGet(), 36);
        Arrays.fill(entropy, (byte) 0);
        return new ScanSession(new CanarySet(nonce));
    }

    public final class ScanSession {
        private final CanarySet canaries;
        private boolean consumed;

        private ScanSession(CanarySet canaries) {
            this.canaries = canaries;
        }

        public CanarySet canaries() {
            return canaries;
        }

        /** Consumes this one-shot session and scans only the three real in-process readers. */
        synchronized RuntimeScan scanRuntime(
                String independentlyComputedSubjectDigest, List<Surface> surfaces) {
            if (consumed || independentlyComputedSubjectDigest == null
                    || !SHA256.matcher(independentlyComputedSubjectDigest).matches()) {
                throw rejected();
            }
            consumed = true;
            Objects.requireNonNull(surfaces, "surfaces");
            EnumMap<SurfaceTarget, Surface> exact = new EnumMap<>(SurfaceTarget.class);
            for (Surface surface : surfaces) {
                if (surface == null || exact.putIfAbsent(surface.target(), surface) != null) {
                    throw rejected();
                }
            }
            if (!exact.keySet().equals(RUNTIME_TARGETS)) {
                throw rejected();
            }

            List<PatternBytes> patterns = patterns(canaries);
            try {
                proveSeededSensitivity(patterns);
                long totalBytes = 0;
                List<TargetResult> targets = new ArrayList<>(RUNTIME_TARGETS.size());
                for (SurfaceTarget target : SurfaceTarget.values()) {
                    if (!RUNTIME_TARGETS.contains(target)) {
                        continue;
                    }
                    Surface surface = exact.get(target);
                    if (!target.readerIdentity().equals(surface.readerIdentity())
                            || surface.items().isEmpty()
                            || surface.items().size() > MAXIMUM_ITEMS_PER_TARGET) {
                        throw rejected();
                    }
                    Set<String> itemIds = new HashSet<>();
                    int matches = 0;
                    for (SurfaceItem item : surface.items()) {
                        if (!itemIds.add(item.identity())) {
                            throw rejected();
                        }
                        ScanCounts counts = scanItem(item, patterns);
                        totalBytes = checkedAdd(totalBytes, counts.bytes(), MAXIMUM_TOTAL_BYTES);
                        try {
                            matches = Math.addExact(matches, counts.matches());
                        } catch (ArithmeticException exception) {
                            throw rejected();
                        }
                    }
                    targets.add(new TargetResult(
                            target.id(), target.readerIdentity(), surface.items().size(), matches,
                            TargetResult.SENSITIVITY_PROVEN));
                }
                return new RuntimeScan(independentlyComputedSubjectDigest, targets);
            } finally {
                patterns.forEach(pattern -> Arrays.fill(pattern.bytes(), (byte) 0));
            }
        }
    }

    record RuntimeScan(String subjectDigest, List<TargetResult> targets) {
        RuntimeScan {
            subjectDigest = Objects.requireNonNull(subjectDigest, "subjectDigest");
            targets = List.copyOf(targets);
        }
    }

    /** Run-owned values for seeding business paths; string rendering is always redacted. */
    public static final class CanarySet {
        private final Map<CanaryKind, String> values;
        private final Map<CanaryKind, String> hashes;

        private CanarySet(String nonce) {
            Map<CanaryKind, String> generated = new EnumMap<>(CanaryKind.class);
            // Reserve a valid-shaped, low-collision namespace so artifact scans detect
            // only run-owned phone canaries rather than arbitrary digits in SHA-256 values.
            generated.put(CanaryKind.PHONE, shapedDigits("199999", nonce, 5));
            generated.put(CanaryKind.IDENTITY, shapedDigits("110101", nonce, 12));
            generated.put(CanaryKind.CREDENTIAL, "cred_" + nonce);
            generated.put(CanaryKind.OBJECT, "object_" + nonce);
            generated.put(CanaryKind.DEK, "dek_" + nonce);
            generated.put(CanaryKind.CAPABILITY, "ocap_v1_" + nonce);
            generated.put(CanaryKind.URL, "https://canary.invalid/" + nonce);
            generated.put(CanaryKind.CRLF, "canary\r\n" + nonce);
            values = Map.copyOf(generated);
            Map<CanaryKind, String> generatedHashes = new EnumMap<>(CanaryKind.class);
            generated.forEach((kind, value) -> generatedHashes.put(
                    kind, sha256(value.getBytes(StandardCharsets.UTF_8))));
            hashes = Map.copyOf(generatedHashes);
        }

        public String value(CanaryKind kind) {
            return values.get(Objects.requireNonNull(kind, "kind"));
        }

        public Map<CanaryKind, String> hashes() {
            return hashes;
        }

        @Override
        public String toString() {
            return "CanarySet[count=" + values.size() + ", values=[redacted]]";
        }
    }

    public record Surface(
            SurfaceTarget target, String readerIdentity, List<SurfaceItem> items) {
        public Surface {
            Objects.requireNonNull(target, "target");
            if (!target.readerIdentity().equals(readerIdentity)) {
                throw rejected();
            }
            items = List.copyOf(Objects.requireNonNull(items, "items"));
        }
    }

    public record SurfaceItem(String identity, InputStreamSupplier opener) {
        public SurfaceItem {
            if (identity == null || !ITEM_ID.matcher(identity).matches() || opener == null) {
                throw rejected();
            }
        }

        public static SurfaceItem bytes(String identity, byte[] value) {
            Objects.requireNonNull(value, "value");
            byte[] snapshot = value.clone();
            return new SurfaceItem(identity, () -> new ByteArrayInputStream(snapshot));
        }
    }

    @FunctionalInterface
    public interface InputStreamSupplier {
        InputStream open() throws IOException;
    }

    private static ScanCounts scanItem(SurfaceItem item, List<PatternBytes> patterns) {
        try (InputStream input = Objects.requireNonNull(item.opener().open(), "input")) {
            return scanStream(input, patterns, MAXIMUM_ITEM_BYTES);
        } catch (IOException | RuntimeException exception) {
            throw rejected();
        }
    }

    private static ScanCounts scanStream(
            InputStream input, List<PatternBytes> patterns, long maximumBytes) throws IOException {
        int overlapBytes = patterns.stream()
                .mapToInt(pattern -> pattern.bytes().length)
                .max().orElseThrow() - 1;
        byte[] readBuffer = new byte[READ_BUFFER_BYTES];
        byte[] overlap = new byte[Math.max(0, overlapBytes)];
        int overlapLength = 0;
        long total = 0;
        Set<String> findings = new HashSet<>();
        try {
            while (true) {
                int read = input.read(readBuffer);
                if (read < 0) {
                    break;
                }
                if (read == 0) {
                    int single = input.read();
                    if (single < 0) {
                        break;
                    }
                    readBuffer[0] = (byte) single;
                    read = 1;
                }
                total = checkedAdd(total, read, maximumBytes);
                byte[] window = new byte[overlapLength + read];
                try {
                    System.arraycopy(overlap, 0, window, 0, overlapLength);
                    System.arraycopy(readBuffer, 0, window, overlapLength, read);
                    for (PatternBytes pattern : patterns) {
                        if (contains(window, pattern.bytes())) {
                            findings.add(pattern.findingId());
                        }
                    }
                    overlapLength = Math.min(overlap.length, window.length);
                    if (overlapLength > 0) {
                        System.arraycopy(window, window.length - overlapLength,
                                overlap, 0, overlapLength);
                    }
                } finally {
                    Arrays.fill(window, (byte) 0);
                }
            }
            return new ScanCounts(total, findings.size());
        } finally {
            Arrays.fill(readBuffer, (byte) 0);
            Arrays.fill(overlap, (byte) 0);
        }
    }

    private static void proveSeededSensitivity(List<PatternBytes> patterns) {
        for (PatternBytes pattern : patterns) {
            byte[] prefix = new byte[READ_BUFFER_BYTES - 3];
            Arrays.fill(prefix, (byte) 'x');
            byte[] seeded = new byte[prefix.length + pattern.bytes().length];
            System.arraycopy(prefix, 0, seeded, 0, prefix.length);
            System.arraycopy(pattern.bytes(), 0, seeded, prefix.length, pattern.bytes().length);
            try {
                if (scanStream(new FragmentedInputStream(seeded, 5), patterns,
                        MAXIMUM_ITEM_BYTES).matches() < 1) {
                    throw rejected();
                }
            } catch (IOException exception) {
                throw rejected();
            } finally {
                Arrays.fill(prefix, (byte) 0);
                Arrays.fill(seeded, (byte) 0);
            }
        }
    }

    private static List<PatternBytes> patterns(CanarySet canaries) {
        Map<String, byte[]> unique = new LinkedHashMap<>();
        canaries.values.forEach((kind, value) -> {
            byte[] raw = value.getBytes(StandardCharsets.UTF_8);
            byte[] base64 = Base64.getEncoder().encode(raw);
            byte[] base64url = Base64.getUrlEncoder().withoutPadding().encode(raw);
            byte[] hex = HexFormat.of().formatHex(raw).getBytes(StandardCharsets.US_ASCII);
            byte[] url = URLEncoder.encode(value, StandardCharsets.UTF_8)
                    .getBytes(StandardCharsets.US_ASCII);
            try {
                addPattern(unique, raw);
                addPattern(unique, base64);
                addPattern(unique, base64url);
                addPattern(unique, hex);
                addPattern(unique, url);
            } finally {
                Arrays.fill(raw, (byte) 0);
                Arrays.fill(base64, (byte) 0);
                Arrays.fill(base64url, (byte) 0);
                Arrays.fill(hex, (byte) 0);
                Arrays.fill(url, (byte) 0);
            }
        });
        return unique.entrySet().stream()
                .map(entry -> new PatternBytes(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static void addPattern(Map<String, byte[]> patterns, byte[] bytes) {
        if (bytes.length > 0) {
            patterns.putIfAbsent(sha256(bytes), bytes.clone());
        }
    }

    private static boolean contains(byte[] input, byte[] pattern) {
        outer:
        for (int offset = 0; offset <= input.length - pattern.length; offset++) {
            for (int index = 0; index < pattern.length; index++) {
                if (input[offset + index] != pattern[index]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static long checkedAdd(long current, long addition, long maximum) {
        try {
            long result = Math.addExact(current, addition);
            if (result > maximum) {
                throw rejected();
            }
            return result;
        } catch (ArithmeticException exception) {
            throw rejected();
        }
    }

    private static String shapedDigits(String prefix, String nonce, int count) {
        StringBuilder output = new StringBuilder(prefix);
        byte[] digest = sha256Bytes(nonce.getBytes(StandardCharsets.US_ASCII));
        for (int index = 0; index < count; index++) {
            output.append(Byte.toUnsignedInt(digest[index]) % 10);
        }
        Arrays.fill(digest, (byte) 0);
        return output.toString();
    }

    private static String sha256(byte[] bytes) {
        return HexFormat.of().formatHex(sha256Bytes(bytes));
    }

    private static byte[] sha256Bytes(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Java 21 must provide SHA-256", exception);
        }
    }

    private static IllegalStateException rejected() {
        return new IllegalStateException("sensitive data leak scan rejected");
    }

    private record PatternBytes(String findingId, byte[] bytes) {
        private PatternBytes {
            findingId = Objects.requireNonNull(findingId, "findingId");
            bytes = Objects.requireNonNull(bytes, "bytes");
        }
    }

    private record ScanCounts(long bytes, int matches) {
    }

    private static final class FragmentedInputStream extends InputStream {
        private final ByteArrayInputStream delegate;
        private final int fragmentBytes;

        private FragmentedInputStream(byte[] value, int fragmentBytes) {
            delegate = new ByteArrayInputStream(value);
            this.fragmentBytes = fragmentBytes;
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(byte[] output, int offset, int length) {
            return delegate.read(output, offset, Math.min(length, fragmentBytes));
        }
    }
}
