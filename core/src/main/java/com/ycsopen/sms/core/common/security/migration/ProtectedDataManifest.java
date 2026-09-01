package com.ycsopen.sms.core.common.security.migration;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataTarget.BlindIndexRule;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataTarget.Kind;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataTarget.LegacyRule;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataTarget.MigrationState;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataTarget.NullPolicy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Loads the reviewed protected-data inventory into immutable, allowlisted migration targets. */
public final class ProtectedDataManifest {

    public static final String VERSION = "ycs-protected-data-inventory/v1";
    public static final int MAXIMUM_MANIFEST_BYTES = 1_048_576;
    private static final long ENVELOPE_OVERHEAD = 145;
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build());

    private static final Map<String, ReviewedRule> REVIEWED_INLINE = reviewedInline();
    private static final Map<String, ReviewedRule> REVIEWED_OBJECTS = reviewedObjects();
    private static final Map<String, ReviewedRule> REVIEWED_DIGESTS = reviewedDigests();
    private static final Set<String> REVIEWED_CANDIDATES = Set.of(
            "users.password_hash",
            "users.avatar_url",
            "tenants.unified_social_credit_code",
            "tenants.legal_rep_name",
            "tenants.contact_name",
            "tenants.registered_address",
            "tenants.business_address",
            "tenants.contract_attachment_url",
            "tenant_callback_configs.delivery_callback_url",
            "tenant_callback_configs.uplink_callback_url",
            "tenant_callback_configs.unsubscribe_callback_url",
            "delivery_reports.raw_payload",
            "uplink_records.push_url",
            "short_links.target_url",
            "operation_logs.request_url");
    private static final Set<String> REVIEWED_SOURCE_SURFACES = Set.of(
            "message-submit-persistence",
            "tenant-registration-persistence",
            "auth-user-hydration-save",
            "hmac-api-key-hydration",
            "blacklist-lookup-hydration",
            "tenant-lifecycle-analytics-hydration-save");
    private static final Set<String> TOP_LEVEL_KEYS = Set.of(
            "manifest_version", "manifest_schema", "envelope_contract", "targets",
            "digest_targets", "candidates", "source_surfaces", "obligation_readiness");

    private final String digest;
    private final Map<String, ProtectedDataTarget> targets;
    private final List<String> unresolvedReasons;

    private ProtectedDataManifest(
            String digest,
            Map<String, ProtectedDataTarget> targets,
            List<String> unresolvedReasons) {
        this.digest = digest;
        this.targets = Map.copyOf(targets);
        this.unresolvedReasons = List.copyOf(unresolvedReasons);
    }

    public static ProtectedDataManifest load(Path path, String expectedDigest) {
        Objects.requireNonNull(path, "path");
        try {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
                throw invalid("manifest path must be one regular non-symlink file");
            }
            try (InputStream input = Files.newInputStream(path)) {
                return load(input, expectedDigest);
            }
        } catch (IOException exception) {
            throw invalid("manifest could not be read");
        }
    }

    public static ProtectedDataManifest load(InputStream input, String expectedDigest) {
        Objects.requireNonNull(input, "input");
        byte[] bytes = readBounded(input);
        return parse(bytes, expectedDigest);
    }

    public static String canonicalDigest(byte[] canonicalManifestBytes) {
        Objects.requireNonNull(canonicalManifestBytes, "canonicalManifestBytes");
        requireCanonicalUtf8(canonicalManifestBytes);
        try {
            return "sha256:" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonicalManifestBytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Java 21 must provide SHA-256", exception);
        }
    }

    public String digest() {
        return digest;
    }

    public Map<String, ProtectedDataTarget> targets() {
        return targets;
    }

    public ProtectedDataTarget requireTarget(String id) {
        ProtectedDataTarget target = targets.get(id);
        if (target == null) {
            throw invalid("target is not in the reviewed manifest");
        }
        return target;
    }

    public boolean resolvedForMigration() {
        return unresolvedReasons.isEmpty();
    }

    public List<String> unresolvedReasons() {
        return unresolvedReasons;
    }

    public Set<String> blindIndexTargetIds() {
        Set<String> ids = new HashSet<>();
        targets.values().stream().filter(ProtectedDataTarget::requiresBlindIndex)
                .map(ProtectedDataTarget::id).forEach(ids::add);
        return Set.copyOf(ids);
    }

    public Set<String> noIndexTargetIds() {
        Set<String> ids = new HashSet<>();
        targets.values().stream()
                .filter(target -> target.blindIndexRule() == BlindIndexRule.EXCLUDED_NO_EQUALITY_CONTRACT)
                .map(ProtectedDataTarget::id).forEach(ids::add);
        return Set.copyOf(ids);
    }

    private static ProtectedDataManifest parse(byte[] bytes, String expectedDigest) {
        String actualDigest = canonicalDigest(bytes);
        if (!constantTimeEquals(actualDigest, normalizeDigest(expectedDigest))) {
            throw invalid("manifest digest does not match the reviewed digest");
        }
        try {
            JsonNode root = JSON.readTree(bytes);
            if (root == null || !root.isObject() || !fieldNames(root).equals(TOP_LEVEL_KEYS)) {
                throw invalid("manifest top-level fields are not canonical");
            }
            if (!VERSION.equals(requiredText(root, "manifest_version"))) {
                throw invalid("manifest version is not supported");
            }
            requireEnvelopeContract(root.required("envelope_contract"));

            Map<String, ProtectedDataTarget> parsed = new LinkedHashMap<>();
            parseTargets(root.required("targets"), REVIEWED_INLINE, REVIEWED_OBJECTS, parsed);
            parseDigestTargets(root.required("digest_targets"), parsed);
            Set<String> expectedIds = new HashSet<>(REVIEWED_INLINE.keySet());
            expectedIds.addAll(REVIEWED_OBJECTS.keySet());
            expectedIds.addAll(REVIEWED_DIGESTS.keySet());
            if (!parsed.keySet().equals(expectedIds)) {
                throw invalid("manifest target set differs from the reviewed allowlist");
            }

            List<String> unresolved = new ArrayList<>();
            validateCandidates(root.required("candidates"), unresolved);
            validateSourceSurfaces(root.required("source_surfaces"), unresolved);
            validateReadiness(root.required("obligation_readiness"), unresolved);
            return new ProtectedDataManifest(actualDigest, parsed, unresolved);
        } catch (IOException | IllegalArgumentException exception) {
            if (exception instanceof ManifestException manifestException) {
                throw manifestException;
            }
            throw invalid("manifest JSON is invalid");
        }
    }

    private static void parseTargets(
            JsonNode rows,
            Map<String, ReviewedRule> inline,
            Map<String, ReviewedRule> objects,
            Map<String, ProtectedDataTarget> parsed) {
        if (!rows.isArray()) {
            throw invalid("targets must be an array");
        }
        for (JsonNode row : rows) {
            String id = requiredText(row, "id");
            ReviewedRule rule = inline.get(id);
            Kind kind = Kind.DATABASE_FIELD;
            if (rule == null) {
                rule = objects.get(id);
                kind = Kind.PROTECTED_OBJECT_REFERENCE;
            }
            if (rule == null || parsed.containsKey(id)) {
                throw invalid("target is unknown or duplicated");
            }
            if (!"PROTECTED".equals(requiredText(row, "classification"))
                    || "REVIEW_REQUIRED".equals(row.path("classification").asText())
                    || "DEFERRED_OWNER".equals(row.path("migration_state").asText())) {
                throw invalid("current or migratable target is unresolved");
            }
            MigrationState state = parseMigrationState(requiredText(row, "migration_state"));
            BlindIndexRule blindIndex = parseBlindIndex(requiredText(row, "blind_index"));
            if (blindIndex != rule.blindIndexRule()) {
                throw invalid("target blind-index disposition differs from review");
            }
            long sourceBound = requiredPositiveLong(row, "source_bound_bytes");
            long maximumEnvelope = requiredPositiveLong(row, "maximum_complete_envelope_bytes");
            long storageCapacity = requiredPositiveLong(row, "storage_capacity_bytes");
            if (sourceBound != rule.sourceBoundBytes()
                    || maximumEnvelope != sourceBound + ENVELOPE_OVERHEAD
                    || storageCapacity != rule.storageCapacityBytes()) {
                throw invalid("target capacity differs from the canonical contract");
            }
            ProtectedDataTarget target = new ProtectedDataTarget(
                    id,
                    requiredText(row, "table"),
                    requiredText(row, "column"),
                    kind,
                    state,
                    blindIndex,
                    rule.legacyRule(),
                    rule.nullPolicy(),
                    sourceBound,
                    maximumEnvelope,
                    storageCapacity,
                    nullableText(row, "tenant_column"),
                    requiredText(row, "identity_column"));
            parsed.put(id, target);
        }
    }

    private static void parseDigestTargets(JsonNode rows, Map<String, ProtectedDataTarget> parsed) {
        if (!rows.isArray()) {
            throw invalid("digest_targets must be an array");
        }
        for (JsonNode row : rows) {
            String id = requiredText(row, "id");
            ReviewedRule rule = REVIEWED_DIGESTS.get(id);
            if (rule == null || parsed.containsKey(id)) {
                throw invalid("digest target is unknown or duplicated");
            }
            if (!"LEGACY_SHA256_MIGRATION_TARGET".equals(requiredText(row, "classification"))) {
                throw invalid("digest target classification differs from review");
            }
            MigrationState state = parseMigrationState(requiredText(row, "migration_state"));
            parsed.put(id, new ProtectedDataTarget(
                    id,
                    requiredText(row, "table"),
                    requiredText(row, "column"),
                    Kind.LEGACY_DIGEST,
                    state,
                    BlindIndexRule.REQUIRED_VERSIONED_HMAC,
                    LegacyRule.LOWERCASE_SHA256_HEX,
                    NullPolicy.FORBIDDEN,
                    64,
                    64,
                    64,
                    rule.tenantColumn(),
                    rule.identityColumn()));
        }
    }

    private static void validateCandidates(JsonNode rows, List<String> unresolved) {
        if (!rows.isArray()) {
            throw invalid("candidates must be an array");
        }
        Set<String> ids = new HashSet<>();
        for (JsonNode row : rows) {
            String id = requiredText(row, "id");
            if (!REVIEWED_CANDIDATES.contains(id) || !ids.add(id)) {
                throw invalid("candidate is unknown or duplicated");
            }
            String classification = requiredText(row, "classification");
            boolean executable = requiredBoolean(row, "executable");
            boolean migratable = requiredBoolean(row, "migratable");
            if ("REVIEW_REQUIRED".equals(classification)) {
                unresolved.add("candidate-review-required:" + id);
            }
            if ("DEFERRED_OWNER".equals(classification) && (executable || migratable)) {
                unresolved.add("current-candidate-deferred:" + id);
            }
        }
        if (!ids.equals(REVIEWED_CANDIDATES)) {
            throw invalid("candidate set differs from the reviewed allowlist");
        }
    }

    private static void validateSourceSurfaces(JsonNode rows, List<String> unresolved) {
        if (!rows.isArray()) {
            throw invalid("source_surfaces must be an array");
        }
        Set<String> ids = new HashSet<>();
        for (JsonNode row : rows) {
            String id = requiredText(row, "id");
            if (!REVIEWED_SOURCE_SURFACES.contains(id) || !ids.add(id)) {
                throw invalid("source surface is unknown or duplicated");
            }
            if (requiredBoolean(row, "obligation_blocking")) {
                unresolved.add("source-surface-blocking:" + id);
            }
        }
        if (!ids.equals(REVIEWED_SOURCE_SURFACES)) {
            throw invalid("source surface set differs from the reviewed allowlist");
        }
    }

    private static void validateReadiness(JsonNode readiness, List<String> unresolved) {
        String status = requiredText(readiness, "status");
        JsonNode blocking = readiness.required("blocking_surface_ids");
        if (!blocking.isArray()) {
            throw invalid("blocking_surface_ids must be an array");
        }
        Set<String> ids = new HashSet<>();
        blocking.forEach(node -> {
            if (!node.isTextual() || !ids.add(node.textValue())) {
                throw invalid("blocking surface identifiers must be unique text");
            }
        });
        if ("READY".equals(status) != ids.isEmpty()) {
            throw invalid("readiness and blocking surfaces disagree");
        }
        ids.forEach(id -> unresolved.add("readiness-blocking:" + id));
    }

    private static void requireEnvelopeContract(JsonNode contract) {
        if (!"YCSE/v1".equals(requiredText(contract, "version"))
                || requiredPositiveLong(contract, "maximum_overhead_bytes") != ENVELOPE_OVERHEAD
                || requiredPositiveLong(contract, "database_plaintext_ceiling_bytes") != 110
                || requiredPositiveLong(contract, "opaque_object_id_ceiling_bytes") != 64) {
            throw invalid("envelope contract differs from YCSE/v1");
        }
    }

    private static byte[] readBounded(InputStream input) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream(32_768);
            byte[] buffer = new byte[8_192];
            int total = 0;
            while (total <= MAXIMUM_MANIFEST_BYTES) {
                int read = input.read(buffer, 0, Math.min(buffer.length, MAXIMUM_MANIFEST_BYTES + 1 - total));
                if (read < 0) {
                    return output.toByteArray();
                }
                if (read == 0) {
                    int one = input.read();
                    if (one < 0) {
                        return output.toByteArray();
                    }
                    output.write(one);
                    total++;
                } else {
                    output.write(buffer, 0, read);
                    total += read;
                }
            }
            throw invalid("manifest exceeds its byte limit");
        } catch (IOException exception) {
            throw invalid("manifest could not be read");
        }
    }

    private static void requireCanonicalUtf8(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > MAXIMUM_MANIFEST_BYTES
                || bytes[bytes.length - 1] != '\n') {
            throw invalid("manifest bytes are not canonical UTF-8 JSON");
        }
        for (byte value : bytes) {
            if (value == '\r') {
                throw invalid("manifest must use LF line endings");
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
        } catch (CharacterCodingException exception) {
            throw invalid("manifest must be canonical UTF-8");
        }
    }

    private static String normalizeDigest(String digest) {
        if (digest == null || !digest.matches("(?:sha256:)?[0-9a-f]{64}")) {
            throw invalid("expected manifest digest is not canonical");
        }
        return digest.startsWith("sha256:") ? digest : "sha256:" + digest;
    }

    private static boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII));
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> fields = new HashSet<>();
        node.fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.required(field);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw invalid(field + " must be nonblank text");
        }
        return value.textValue();
    }

    private static String nullableText(JsonNode node, String field) {
        JsonNode value = node.required(field);
        if (value.isNull()) {
            return null;
        }
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw invalid(field + " must be null or nonblank text");
        }
        return value.textValue();
    }

    private static long requiredPositiveLong(JsonNode node, String field) {
        JsonNode value = node.required(field);
        if (!value.canConvertToLong() || value.longValue() < 1) {
            throw invalid(field + " must be a positive integer");
        }
        return value.longValue();
    }

    private static boolean requiredBoolean(JsonNode node, String field) {
        JsonNode value = node.required(field);
        if (!value.isBoolean()) {
            throw invalid(field + " must be boolean");
        }
        return value.booleanValue();
    }

    private static MigrationState parseMigrationState(String value) {
        try {
            return MigrationState.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw invalid("migration state is not accepted");
        }
    }

    private static BlindIndexRule parseBlindIndex(String value) {
        try {
            return BlindIndexRule.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw invalid("blind-index disposition is not accepted");
        }
    }

    private static ManifestException invalid(String message) {
        return new ManifestException(message);
    }

    private static Map<String, ReviewedRule> reviewedInline() {
        Map<String, ReviewedRule> rules = new HashMap<>();
        addInline(rules, "users.phone_encrypted", 11, true, BlindIndexRule.NOT_APPLICABLE, "tenant_id", "id");
        addInline(rules, "tenants.legal_rep_id_no_encrypted", 18, true, BlindIndexRule.NOT_APPLICABLE, "id", "id");
        addInline(rules, "tenants.contact_id_no_encrypted", 18, true, BlindIndexRule.NOT_APPLICABLE, "id", "id");
        addInline(rules, "tenants.contact_phone_encrypted", 11, true, BlindIndexRule.NOT_APPLICABLE, "id", "id");
        addInline(rules, "signatures.applicant_phone_encrypted", 11, true, BlindIndexRule.NOT_APPLICABLE, "tenant_id", "id");
        addInline(rules, "signatures.applicant_id_no_encrypted", 18, true, BlindIndexRule.NOT_APPLICABLE, "tenant_id", "id");
        addInline(rules, "channels.account_encrypted", 110, true, BlindIndexRule.NOT_APPLICABLE, null, "id");
        addInline(rules, "channels.password_encrypted", 110, true, BlindIndexRule.NOT_APPLICABLE, null, "id");
        addInline(rules, "mobile_portability.mobile_encrypted", 11, false, BlindIndexRule.REQUIRED_VERSIONED_HMAC, null, "mobile_hash");
        addInline(rules, "blacklist_entries.mobile_encrypted", 11, false, BlindIndexRule.REQUIRED_VERSIONED_HMAC, "tenant_id", "id");
        addInline(rules, "tenant_api_keys.app_secret_encrypted", 110, false, BlindIndexRule.NOT_APPLICABLE, "tenant_id", "id");
        addInline(rules, "tenant_protocol_credentials.account_encrypted", 110, false, BlindIndexRule.NOT_APPLICABLE, "tenant_id", "id");
        addInline(rules, "tenant_protocol_credentials.password_encrypted", 110, false, BlindIndexRule.NOT_APPLICABLE, "tenant_id", "id");
        addInline(rules, "message_tasks.mobile_encrypted", 11, false, BlindIndexRule.REQUIRED_VERSIONED_HMAC, "tenant_id", "message_id");
        addInline(rules, "bulk_sending_items.mobile_encrypted", 11, false, BlindIndexRule.EXCLUDED_NO_EQUALITY_CONTRACT, null, "id");
        addInline(rules, "uplink_records.mobile_encrypted", 11, false, BlindIndexRule.EXCLUDED_NO_EQUALITY_CONTRACT, "tenant_id", "id");
        addInline(rules, "unsubscribe_records.mobile_encrypted", 11, false, BlindIndexRule.REQUIRED_VERSIONED_HMAC, "tenant_id", "id");
        return Map.copyOf(rules);
    }

    private static Map<String, ReviewedRule> reviewedObjects() {
        Map<String, ReviewedRule> rules = new HashMap<>();
        addObject(rules, "tenants.business_license_url", 10_485_760, "id", "id");
        addObject(rules, "tenants.legal_rep_id_front_url", 5_242_880, "id", "id");
        addObject(rules, "tenants.legal_rep_id_back_url", 5_242_880, "id", "id");
        addObject(rules, "tenants.shortlink_domain_proof_url", 10_485_760, "id", "id");
        addObject(rules, "tenants.trademark_proof_url", 10_485_760, "id", "id");
        addObject(rules, "signatures.evidence_url", 10_485_760, "tenant_id", "id");
        addObject(rules, "export_tasks.file_url", 10_485_760, null, "id");
        return Map.copyOf(rules);
    }

    private static Map<String, ReviewedRule> reviewedDigests() {
        return Map.of(
                "mobile_portability.mobile_hash", digestRule(null, "mobile_hash"),
                "blacklist_entries.mobile_hash", digestRule("tenant_id", "id"),
                "third_party_risk_check_logs.mobile_hash", digestRule(null, "id"),
                "message_tasks.mobile_hash", digestRule("tenant_id", "id"),
                "unsubscribe_records.mobile_hash", digestRule("tenant_id", "id"));
    }

    private static void addInline(
            Map<String, ReviewedRule> rules,
            String id,
            long bound,
            boolean nullable,
            BlindIndexRule blindIndex,
            String tenantColumn,
            String identityColumn) {
        rules.put(id, new ReviewedRule(
                bound, 255, blindIndex, LegacyRule.UTF8_PLAINTEXT,
                nullable ? NullPolicy.ALLOWED : NullPolicy.FORBIDDEN, tenantColumn, identityColumn));
    }

    private static void addObject(
            Map<String, ReviewedRule> rules,
            String id,
            long bound,
            String tenantColumn,
            String identityColumn) {
        rules.put(id, new ReviewedRule(
                bound, 255, BlindIndexRule.NOT_APPLICABLE,
                LegacyRule.OPAQUE_OBJECT_ID_OR_HTTPS_URL, NullPolicy.ALLOWED,
                tenantColumn, identityColumn));
    }

    private static ReviewedRule digestRule(String tenantColumn, String identityColumn) {
        return new ReviewedRule(
                64, 64, BlindIndexRule.REQUIRED_VERSIONED_HMAC,
                LegacyRule.LOWERCASE_SHA256_HEX, NullPolicy.FORBIDDEN,
                tenantColumn, identityColumn);
    }

    private record ReviewedRule(
            long sourceBoundBytes,
            long storageCapacityBytes,
            BlindIndexRule blindIndexRule,
            LegacyRule legacyRule,
            NullPolicy nullPolicy,
            String tenantColumn,
            String identityColumn) {
    }

    public static final class ManifestException extends IllegalArgumentException {
        private ManifestException(String message) {
            super(message);
        }
    }
}
