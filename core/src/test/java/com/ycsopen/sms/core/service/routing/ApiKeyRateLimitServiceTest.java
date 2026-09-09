package com.ycsopen.sms.core.service.routing;

import com.ycsopen.sms.core.common.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyRateLimitServiceTest {
    @Mock RedisFixedWindowCounter counter;

    @Test
    void enforcesApiKeySecondMinuteHourAndDayWindowsIndependently() {
        when(counter.increment("api-rate:17:19:SECOND", 1)).thenReturn(1L);
        when(counter.increment("api-rate:17:19:MINUTE", 60)).thenReturn(2L);
        when(counter.increment("api-rate:17:19:HOUR", 3600)).thenReturn(3L);
        when(counter.increment("api-rate:17:19:DAY", 86400)).thenReturn(4L);

        new ApiKeyRateLimitService(counter).enforce(17L, 19L,
                new ApiKeyRateLimitService.RatePolicy(1, 2, 3, 4));

        verify(counter).increment("api-rate:17:19:SECOND", 1);
        verify(counter).increment("api-rate:17:19:MINUTE", 60);
        verify(counter).increment("api-rate:17:19:HOUR", 3600);
        verify(counter).increment("api-rate:17:19:DAY", 86400);
    }

    @Test
    void exceededWindowThrowsStandardRateLimitException() {
        when(counter.increment("api-rate:17:19:SECOND", 1)).thenReturn(2L);

        assertThatThrownBy(() -> new ApiKeyRateLimitService(counter).enforce(17L, 19L,
                new ApiKeyRateLimitService.RatePolicy(1, 100, 1000, 10000)))
                .isInstanceOfSatisfying(RateLimitExceededException.class, failure -> {
                    org.assertj.core.api.Assertions.assertThat(failure.getLimitKey()).isEqualTo("api-key-SECOND");
                    org.assertj.core.api.Assertions.assertThat(failure.getRetryAfterSeconds()).isEqualTo(1);
                });
    }
}
