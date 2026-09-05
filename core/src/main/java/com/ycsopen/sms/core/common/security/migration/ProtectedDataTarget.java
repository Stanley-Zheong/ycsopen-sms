package com.ycsopen.sms.core.common.security.migration;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One reviewed physical migration target. Identifiers come only from the checked-in manifest
 * allowlist; migration SQL must never accept caller-supplied table or column names.
 */
public record ProtectedDataTarget(
        String id,
        String table,
        String column,
        Kind kind,
        MigrationState migrationState,
        BlindIndexRule blindIndexRule,
        LegacyRule legacyRule,
        NullPolicy nullPolicy,
        long sourceBoundBytes,
        long maximumCompleteEnvelopeBytes,
        long storageCapacityBytes,
        String tenantColumn,
        String identityColumn) {

    private static final Pattern IDENTIFIER = Pattern.compile("[a-z][a-z0-9_]{0,63}");

    public ProtectedDataTarget {
        requireIdentifier(table, "table");
        requireIdentifier(column, "column");
        if (!Objects.equals(id, table + "." + column)) {
            throw new IllegalArgumentException("target identifier does not match its physical location");
        }
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(migrationState, "migrationState");
        Objects.requireNonNull(blindIndexRule, "blindIndexRule");
        Objects.requireNonNull(legacyRule, "legacyRule");
        Objects.requireNonNull(nullPolicy, "nullPolicy");
        if (sourceBoundBytes < 1 || maximumCompleteEnvelopeBytes < 1 || storageCapacityBytes < 1) {
            throw new IllegalArgumentException("target capacity must be positive");
        }
        if (maximumCompleteEnvelopeBytes > storageCapacityBytes && kind == Kind.DATABASE_FIELD) {
            throw new IllegalArgumentException("database envelope exceeds reviewed storage capacity");
        }
        if (tenantColumn != null) {
            requireIdentifier(tenantColumn, "tenantColumn");
        }
        requireIdentifier(identityColumn, "identityColumn");
        if (blindIndexRule == BlindIndexRule.EXCLUDED_NO_EQUALITY_CONTRACT
                && kind != Kind.DATABASE_FIELD) {
            throw new IllegalArgumentException("no-index disposition applies only to protected fields");
        }
    }

    public Optional<String> tenantColumnName() {
        return Optional.ofNullable(tenantColumn);
    }

    public boolean requiresBlindIndex() {
        return blindIndexRule == BlindIndexRule.REQUIRED_VERSIONED_HMAC
                || kind == Kind.LEGACY_DIGEST;
    }

    public long maximumStoredValueBytes() {
        return switch (kind) {
            case DATABASE_FIELD -> maximumCompleteEnvelopeBytes;
            case PROTECTED_OBJECT_REFERENCE -> 64;
            case LEGACY_DIGEST -> 64;
        };
    }

    private static void requireIdentifier(String value, String field) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is not a reviewed SQL identifier");
        }
    }

    public enum Kind {
        DATABASE_FIELD,
        PROTECTED_OBJECT_REFERENCE,
        LEGACY_DIGEST
    }

    public enum MigrationState {
        CURRENT_EXECUTABLE,
        MIGRATABLE_SCHEMA_ONLY
    }

    public enum BlindIndexRule {
        NOT_APPLICABLE,
        REQUIRED_VERSIONED_HMAC,
        EXCLUDED_NO_EQUALITY_CONTRACT
    }

    /** A nonmagic value is legacy only when the reviewed target owns one of these rules. */
    public enum LegacyRule {
        UTF8_PLAINTEXT,
        LOWERCASE_SHA256_HEX,
        OPAQUE_OBJECT_ID_OR_HTTPS_URL,
        NONE
    }

    public enum NullPolicy {
        ALLOWED,
        FORBIDDEN
    }
}
