package com.ycsopen.sms.core.common.security.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.CompositeConverter;
import java.util.List;
import java.util.regex.Pattern;

/** Final output defense applied by every configured appender. */
public final class SecurityRedactionConverter extends CompositeConverter<ILoggingEvent> {

    private static final String REDACTED = "[redacted]";
    private static final List<Redaction> REDACTIONS = List.of(
            redaction("(?i)\\bBearer\\s+[^\\s]+", "[redacted-token]"),
            redaction("\\beyJ[A-Za-z0-9_-]{8,}(?:\\.[A-Za-z0-9_-]+){1,2}\\b", "[redacted-token]"),
            redaction("(?i)\\b(?:ocap|regup)_v1_[A-Za-z0-9_-]+", "[redacted-token]"),
            redaction("(?i)https?://[^\\s]+", "[redacted-url]"),
            redaction("\\bYCSE[A-Za-z0-9+/=_-]{4,}", "[redacted-envelope]"),
            redaction("(?i)\\b(?:mobile|phone|plaintext|ciphertext|password|credential|secret|token|pin|"
                    + "key_material|dek|kek|hmac_key)\\s*[=:]\\s*[^\\s,;]+", REDACTED),
            redaction("\\bCKR_[A-Z0-9_]+\\b", "[redacted-provider]"),
            redaction("(?i)\\b(?:SunPKCS11|PKCS#?11|SoftHSM)[^\\s,;]*", "[redacted-provider]"),
            redaction("(?i)(?:[A-Za-z]:\\\\|/)[^\\s,;]*(?:softhsm|pkcs11)[^\\s,;]*",
                    "[redacted-provider-path]"));

    @Override
    protected String transform(ILoggingEvent event, String input) {
        return redact(input) + System.lineSeparator();
    }

    public static String redact(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String output = stripControls(input);
        for (Redaction redaction : REDACTIONS) {
            output = redaction.pattern().matcher(output).replaceAll(redaction.replacement());
        }
        return output;
    }

    private static String stripControls(String input) {
        StringBuilder sanitized = new StringBuilder(input.length());
        for (int index = 0; index < input.length(); index++) {
            char value = input.charAt(index);
            if (value == '\r' || value == '\n' || Character.isISOControl(value)) {
                sanitized.append(' ');
            } else {
                sanitized.append(value);
            }
        }
        return sanitized.toString();
    }

    private static Redaction redaction(String expression, String replacement) {
        return new Redaction(Pattern.compile(expression), replacement);
    }

    private record Redaction(Pattern pattern, String replacement) {
    }
}
