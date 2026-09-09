package com.ycsopen.sms.core.common.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.common.security.CorrelationIdFilter;
import com.ycsopen.sms.core.common.security.logging.SecurityEventLogger;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    @Test
    void unexpectedFailureReturnsOnlySafeMessageAndMatchingCorrelationId() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(new GlobalExceptionHandler(mock(SecurityEventLogger.class)))
                .addFilters(new CorrelationIdFilter())
                .build();

        var response = mvc.perform(get("/phase5/failure"))
                .andExpect(status().isInternalServerError())
                .andReturn()
                .getResponse();

        String correlation = response.getHeader(CorrelationIdFilter.HEADER);
        var body = new ObjectMapper().readTree(response.getContentAsByteArray());
        String responseBody = new String(response.getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(correlation).matches("[0-9a-f]{32}");
        assertThat(body.path("traceId").asText()).isEqualTo(correlation);
        assertThat(body.path("message").asText()).isEqualTo("系统繁忙，请稍后再试");
        assertThat(responseBody).doesNotContain("database-password", "RuntimeException", "stack");
    }

    @Test
    void authorizationFailureRemainsForbiddenInsteadOfBecoming500() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(new GlobalExceptionHandler(mock(SecurityEventLogger.class)))
                .build();

        mvc.perform(get("/phase5/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void invalidRequestRemainsBadRequestInsteadOfBecoming500() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(new GlobalExceptionHandler(mock(SecurityEventLogger.class)))
                .build();

        mvc.perform(post("/phase5/validate")
                        .contentType("application/json")
                        .content("{\"value\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void explicitClientStatusRemainsBadRequestInsteadOfBecoming500() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(new GlobalExceptionHandler(mock(SecurityEventLogger.class)))
                .build();

        mvc.perform(get("/phase6/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请求参数不合法"));
    }

    @Test
    void rateLimitFailureReturnsStandard429Contract() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(new GlobalExceptionHandler(mock(SecurityEventLogger.class)))
                .build();

        mvc.perform(get("/phase18/rate-limit"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(429))
                .andExpect(jsonPath("$.message").value("请求过于频繁，请稍后重试"))
                .andExpect(jsonPath("$.data.limitKey").value("api-key-SECOND"))
                .andExpect(jsonPath("$.data.retryAfterSeconds").value(1))
                .andExpect(jsonPath("$.data.guidance").value("稍后重试，不会创建任务或扣费"));
    }

    @RestController
    static class FailureController {
        @GetMapping("/phase5/failure")
        void fail() {
            throw new RuntimeException("database-password=must-never-leak");
        }

        @GetMapping("/phase5/forbidden")
        void forbidden() {
            throw new AccessDeniedException("must-not-become-500");
        }

        @GetMapping("/phase6/bad-request")
        void badRequest() {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "internal detail");
        }

        @GetMapping("/phase18/rate-limit")
        void rateLimit() {
            throw new RateLimitExceededException("api-key-SECOND", 1);
        }

        @PostMapping("/phase5/validate")
        void validate(@Valid @RequestBody ValidationBody body) {
        }
    }

    record ValidationBody(@NotBlank String value) { }
}
