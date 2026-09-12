package com.ycsopen.sms.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.common.security.HmacSignatureVerifier;
import com.ycsopen.sms.core.service.delivery.HttpSmsUpstreamProviderClient;
import com.ycsopen.sms.core.service.delivery.SandboxSmsUpstreamProviderClient;
import com.ycsopen.sms.core.service.delivery.SmsUpstreamProviderClient;
import com.ycsopen.sms.core.service.exemption.ExemptionPolicyService;
import com.ycsopen.sms.core.service.routing.RedisFixedWindowCounter;
import com.ycsopen.sms.core.service.tool.NumberAttributionService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeSpringWiringTest {

    @Test
    void selectsTheRuntimeRedisConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class));
            context.register(RedisFixedWindowCounter.class);

            context.refresh();

            assertThat(context.getBean(RedisFixedWindowCounter.class)).isNotNull();
        }
    }

    @Test
    void selectsRuntimeConstructorsForServicesWithTestSeams() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class));
            context.register(ExemptionPolicyService.class, NumberAttributionService.class);

            context.refresh();

            assertThat(context.getBean(ExemptionPolicyService.class)).isNotNull();
            assertThat(context.getBean(NumberAttributionService.class)).isNotNull();
        }
    }

    @Test
    void usesRedisForHmacNonceWhenAvailable() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent("hmac-nonce:runtime-nonce", "1", Duration.ofMinutes(5))).thenReturn(true);

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(StringRedisTemplate.class, () -> redis);
            context.register(HmacSignatureVerifier.class);
            context.refresh();

            assertThat(context.getBean(HmacSignatureVerifier.class).checkAndRecordNonce("runtime-nonce")).isTrue();
            verify(values).setIfAbsent("hmac-nonce:runtime-nonce", "1", Duration.ofMinutes(5));
        }
    }

    @Test
    void providesSandboxUpstreamByDefault() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(SandboxSmsUpstreamProviderClient.class);
            context.refresh();

            assertThat(context.getBeansOfType(SmsUpstreamProviderClient.class))
                    .containsOnlyKeys("sandboxSmsUpstreamProviderClient");
        }
    }

    @Test
    void selectsHttpUpstreamWhenExplicitlyEnabled() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "ycsopen.sms.upstream-http.enabled", "true",
                    "ycsopen.sms.upstream-http.base-url", "http://127.0.0.1:9")));
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.register(SandboxSmsUpstreamProviderClient.class, HttpSmsUpstreamProviderClient.class);
            context.refresh();

            assertThat(context.getBeansOfType(SmsUpstreamProviderClient.class))
                    .containsOnlyKeys("httpSmsUpstreamProviderClient");
        }
    }
}
