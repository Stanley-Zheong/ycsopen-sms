package com.ycsopen.sms.core.service.configuration;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Closed registry for the small set of platform settings that may change at runtime. */
@Component
public final class PlatformConfigurationRegistry {
    public static final String LOGIN_MAX_FAILURES = "security.login.max-failures";
    public static final String UNUSUAL_IP_ENABLED = "security.login.unusual-ip-enabled";
    public static final String EXPORT_SIGNING_KEY_REFERENCE = "security.export.signing-key-ref";
    public static final String MASKED_SECRET_REFERENCE = "env:••••••";

    private static final Pattern SECRET_REFERENCE = Pattern.compile("env:[A-Z][A-Z0-9_]{1,63}");
    private static final List<Definition> DEFINITIONS = List.of(
            new Definition(LOGIN_MAX_FAILURES, "登录失败锁定阈值", ValueType.INTEGER,
                    "5", "3 到 20 的整数", false),
            new Definition(UNUSUAL_IP_ENABLED, "异常 IP 登录检测", ValueType.BOOLEAN,
                    "true", "true 或 false", false),
            new Definition(EXPORT_SIGNING_KEY_REFERENCE, "导出签名密钥引用", ValueType.SECRET_REFERENCE,
                    "env:YCS_SMS_EXPORT_SIGNING_KEY", "env:UPPER_SNAKE_CASE", true));
    private static final Map<String, Definition> BY_KEY = index();

    public List<Definition> definitions() {
        return DEFINITIONS;
    }

    public Map<String, String> defaults() {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        DEFINITIONS.forEach(definition -> values.put(definition.key(), definition.defaultValue()));
        return Map.copyOf(values);
    }

    public Map<String, String> mergeAndValidate(Map<String, String> current, Map<String, String> changes) {
        Map<String, String> normalizedCurrent = normalizeStored(current);
        LinkedHashMap<String, String> merged = new LinkedHashMap<>(normalizedCurrent);
        if (changes == null) {
            throw new ValidationFailure("changes", "changes are required");
        }
        changes.forEach((key, value) -> {
            Definition definition = BY_KEY.get(key);
            if (definition == null) {
                throw new ValidationFailure(key, "configuration key is not registered: " + key);
            }
            merged.put(key, normalize(definition, value));
        });
        return validateComplete(merged);
    }

    /** Adds defaults for keys introduced after an older immutable snapshot was written. */
    public Map<String, String> normalizeStored(Map<String, String> values) {
        if (values == null) {
            throw new ValidationFailure("snapshot", "stored snapshot is required");
        }
        Set<String> unknown = new HashSet<>(values.keySet());
        unknown.removeAll(BY_KEY.keySet());
        if (!unknown.isEmpty()) {
            throw new ValidationFailure("snapshot", "stored snapshot has unknown keys: " + unknown);
        }
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>(defaults());
        values.forEach((key, value) -> normalized.put(key, normalize(requireDefinition(key), value)));
        return validateComplete(normalized);
    }

    public Map<String, String> validateComplete(Map<String, String> values) {
        if (values == null || !values.keySet().equals(BY_KEY.keySet())) {
            Set<String> actual = values == null ? Set.of() : values.keySet();
            throw new ValidationFailure("snapshot",
                    "snapshot keys do not match the registry; actual=" + actual);
        }
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        DEFINITIONS.forEach(definition -> normalized.put(
                definition.key(), normalize(definition, values.get(definition.key()))));
        return Map.copyOf(normalized);
    }

    public String displayValue(String key, String value) {
        Definition definition = requireDefinition(key);
        return definition.sensitive() ? MASKED_SECRET_REFERENCE : value;
    }

    public Definition requireDefinition(String key) {
        Definition definition = BY_KEY.get(key);
        if (definition == null) {
            throw new ValidationFailure(key, "configuration key is not registered: " + key);
        }
        return definition;
    }

    public String checksum(Map<String, String> values) {
        Map<String, String> normalized = validateComplete(values);
        StringBuilder canonical = new StringBuilder();
        DEFINITIONS.forEach(definition -> canonical.append(definition.key()).append('=')
                .append(normalized.get(definition.key())).append('\n'));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static String normalize(Definition definition, String value) {
        if (value == null) {
            throw new ValidationFailure(definition.key(), "configuration value is required");
        }
        String trimmed = value.trim();
        return switch (definition.type()) {
            case INTEGER -> normalizeInteger(definition.key(), trimmed);
            case BOOLEAN -> normalizeBoolean(definition.key(), trimmed);
            case SECRET_REFERENCE -> normalizeSecretReference(definition.key(), trimmed);
        };
    }

    private static String normalizeInteger(String key, String value) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 3 || parsed > 20) {
                throw new ValidationFailure(key, "integer must be between 3 and 20");
            }
            return Integer.toString(parsed);
        } catch (NumberFormatException error) {
            throw new ValidationFailure(key, "integer value is invalid");
        }
    }

    private static String normalizeBoolean(String key, String value) {
        if (!("true".equals(value) || "false".equals(value))) {
            throw new ValidationFailure(key, "boolean must be true or false");
        }
        return value;
    }

    private static String normalizeSecretReference(String key, String value) {
        if (MASKED_SECRET_REFERENCE.equals(value) || !SECRET_REFERENCE.matcher(value).matches()) {
            throw new ValidationFailure(key, "secret value must be an environment reference");
        }
        return value;
    }

    private static Map<String, Definition> index() {
        LinkedHashMap<String, Definition> result = new LinkedHashMap<>();
        DEFINITIONS.forEach(definition -> result.put(definition.key(), definition));
        return Map.copyOf(result);
    }

    public enum ValueType {
        INTEGER,
        BOOLEAN,
        SECRET_REFERENCE
    }

    public record Definition(String key, String label, ValueType type, String defaultValue,
                             String validation, boolean sensitive) {
    }

    public static final class ValidationFailure extends IllegalArgumentException {
        private final String key;

        public ValidationFailure(String key, String message) {
            super(message);
            this.key = key;
        }

        public String key() {
            return key;
        }
    }
}
