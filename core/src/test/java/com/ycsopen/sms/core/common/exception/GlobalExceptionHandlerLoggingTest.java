package com.ycsopen.sms.core.common.exception;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ycsopen.sms.core.common.security.logging.SecurityEventLogger;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerLoggingTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;
    private GlobalExceptionHandler handler;

    @BeforeEach
    void captureSecurityEvents() {
        logger = (Logger) LoggerFactory.getLogger(SecurityEventLogger.LOGGER_NAME);
        appender = new ListAppender<>();
        appender.setContext(logger.getLoggerContext());
        appender.start();
        logger.addAppender(appender);
        handler = new GlobalExceptionHandler(new SecurityEventLogger());
    }

    @AfterEach
    void releaseCapture() {
        MDC.clear();
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void businessRejectionLogsOnlyStableEventCategoryAndCorrelation() {
        MDC.put("traceId", "trace-business-1");
        BusinessException exception = new BusinessException(
                "TOKEN_ocap_v1_business-secret",
                "mobile=13800138000 https://objects.example/private password=credential-secret");

        var response = handler.handleBusiness(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo(exception.getMessage());
        assertCaptured("BUSINESS_REJECTION", "BUSINESS", "trace-business-1");
        assertAllCanariesAbsent();
    }

    @Test
    void unexpectedNestedProviderFailureNeverLogsMessageOrThrowable() {
        MDC.put("traceId", "trace-unexpected-2");
        Exception nested = new IllegalStateException(
                "CKR_PIN_INCORRECT /usr/lib/softhsm/libsofthsm2.so token=provider-secret");
        Exception exception = new RuntimeException(
                "YCSEQUJDREVGRw== https://user:password@objects.example/private", nested);

        var response = handler.handleUnexpected(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("系统繁忙，请稍后再试");
        assertCaptured("UNEXPECTED_FAILURE", "UNEXPECTED", "trace-unexpected-2");
        assertAllCanariesAbsent();
        assertThat(appender.list.getFirst().getThrowableProxy()).isNull();
        assertThat(appender.list.getFirst().getArgumentArray()).isNull();
    }

    @Test
    void hostileCorrelationCannotInjectASecondLogLine() {
        MDC.put("traceId", "trace-safe\r\nevent=FORGED token=forged-secret");

        handler.handleUnexpected(new RuntimeException("password=nested-secret"));

        assertCaptured("UNEXPECTED_FAILURE", "UNEXPECTED", "invalid");
        assertAllCanariesAbsent();
        assertThat(messages()).allSatisfy(message -> assertThat(message).doesNotContain("\r", "\n", "FORGED"));
    }

    private void assertCaptured(String event, String category, String correlation) {
        assertThat(messages()).singleElement().satisfies(message -> assertThat(message)
                .isEqualTo("security_event event=" + event + " category=" + category
                        + " correlation=" + correlation));
    }

    private void assertAllCanariesAbsent() {
        assertThat(String.join("\n", messages()))
                .doesNotContain("13800138000", "objects.example", "credential-secret", "business-secret",
                        "CKR_PIN_INCORRECT", "libsofthsm2.so", "provider-secret", "YCSEQUJDREVGRw",
                        "nested-secret", "forged-secret");
    }

    private List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
