package com.ycsopen.sms.core.common.security.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityRedactionConverterTest {

    @Test
    void removesCrlfTokenUrlEnvelopeProviderCredentialAndProtectedCanaries() {
        String unsafe = "event=PROVIDER_REJECTION category=PROVIDER correlation=trace-1\r\n"
                + "Bearer bearer-secret ocap_v1_capability-secret regup_v1_upload-secret "
                + "https://user:password@objects.example/private YCSEQUJDREVGRw== "
                + "password=credential-secret mobile=13800138000 ciphertext=raw-ciphertext "
                + "CKR_PIN_INCORRECT SunPKCS11-token /usr/lib/softhsm/libsofthsm2.so";

        String safe = SecurityRedactionConverter.redact(unsafe);

        assertThat(safe)
                .contains("event=PROVIDER_REJECTION", "category=PROVIDER", "correlation=trace-1")
                .doesNotContain("\r", "\n", "bearer-secret", "capability-secret", "upload-secret",
                        "objects.example", "YCSEQUJDREVGRw", "credential-secret", "13800138000",
                        "raw-ciphertext", "CKR_PIN_INCORRECT", "SunPKCS11-token", "libsofthsm2.so");
    }

    @Test
    void capturedAppenderOutputIsSingleLineAndRetainsSafeDiagnostics() {
        Logger logger = (Logger) LoggerFactory.getLogger("security-redaction-test");
        RedactingCaptureAppender appender = new RedactingCaptureAppender();
        appender.setContext(logger.getLoggerContext());
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        boolean originalAdditive = logger.isAdditive();
        logger.setAdditive(false);
        try {
            logger.info("security_event event=PROVIDER_REJECTION category=PROVIDER correlation=trace-2 "
                    + "token=secret-token\nCKR_DEVICE_ERROR");

            assertThat(appender.output()).singleElement().satisfies(line -> assertThat(line)
                    .contains("event=PROVIDER_REJECTION", "category=PROVIDER", "correlation=trace-2")
                    .doesNotContain("secret-token", "CKR_DEVICE_ERROR", "\n", "\r"));
        } finally {
            logger.setAdditive(originalAdditive);
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void everyConfiguredAppenderUsesTheRedactionConverter() throws Exception {
        String configuration = Files.readString(Path.of("src/main/resources/logback-spring.xml"));
        Matcher appenders = Pattern.compile("(?s)<appender\\b.*?</appender>").matcher(configuration);
        int count = 0;
        while (appenders.find()) {
            count++;
            assertThat(appenders.group()).contains("%securityRedact(%msg)");
        }
        assertThat(count).isPositive();
        assertThat(configuration)
                .contains("conversionWord=\"securityRedact\"")
                .contains("SecurityRedactionConverter");
    }

    private static final class RedactingCaptureAppender extends AppenderBase<ILoggingEvent> {
        private final List<String> output = new ArrayList<>();

        @Override
        protected void append(ILoggingEvent event) {
            output.add(SecurityRedactionConverter.redact(event.getFormattedMessage()));
        }

        private List<String> output() {
            return List.copyOf(output);
        }
    }
}
