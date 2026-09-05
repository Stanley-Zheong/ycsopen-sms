package com.ycsopen.sms.core.common.security.logging;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The sole semantic owner for security-sensitive application logging.
 *
 * <p>Callers cannot supply a message template, arbitrary object, exception, token or protected
 * value. Only stable enums and pre-sanitized typed facts can enter the log event.</p>
 */
@Component
public final class SecurityEventLogger {

    public static final String LOGGER_NAME = "security";
    private static final Logger LOG = LoggerFactory.getLogger(LOGGER_NAME);

    public void warn(Event event, Category category, SafeLogValue... facts) {
        log(Level.WARN, event, category, facts);
    }

    public void error(Event event, Category category, SafeLogValue... facts) {
        log(Level.ERROR, event, category, facts);
    }

    public void info(Event event, Category category, SafeLogValue... facts) {
        log(Level.INFO, event, category, facts);
    }

    private static void log(Level level, Event event, Category category, SafeLogValue[] facts) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(facts, "facts");
        StringBuilder message = new StringBuilder(160)
                .append("security_event event=").append(event.name())
                .append(" category=").append(category.name());
        for (SafeLogValue fact : facts) {
            message.append(' ').append(Objects.requireNonNull(fact, "fact").render());
        }
        switch (level) {
            case INFO -> LOG.info(message.toString());
            case WARN -> LOG.warn(message.toString());
            case ERROR -> LOG.error(message.toString());
        }
    }

    public enum Event {
        BUSINESS_REJECTION,
        UNEXPECTED_FAILURE,
        PROTECTED_DATA_REJECTION,
        PROVIDER_REJECTION,
        AUTHORIZATION_REJECTION,
        SECURITY_OPERATION
    }

    public enum Category {
        BUSINESS,
        UNEXPECTED,
        PROTECTED_DATA,
        PROVIDER,
        AUTHORIZATION,
        OPERATION
    }

    private enum Level {
        INFO,
        WARN,
        ERROR
    }
}
