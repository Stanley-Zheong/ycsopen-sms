package com.ycsopen.sms.core.verification;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** OBL-FOUND-TRACE-003: proves real Redis TTL semantics through Spring wiring. */
@SpringBootTest(
        classes = Phase01RedisIntegrationTest.RedisVerificationApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("phase01-integration")
@EnabledIfSystemProperty(named = "phase01.integration.enabled", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Phase01RedisIntegrationTest {

    private static Phase01ServiceSession redis;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        redis = Phase01ServiceHarness.startRedis();
        registry.add("spring.data.redis.host", redis::host);
        registry.add("spring.data.redis.port", redis::port);
    }

    @AfterAll
    void stopRedis() {
        try {
            if (redisTemplate.getConnectionFactory() instanceof LettuceConnectionFactory lettuce) {
                lettuce.destroy();
            }
        } finally {
            if (redis != null) {
                redis.close();
            }
        }
    }

    @Autowired
    StringRedisTemplate redisTemplate;

    @Test
    void performsIsolatedSetTtlGetAndDeleteThroughStringRedisTemplate() {
        String key = "phase01:synthetic:" + UUID.randomUUID();
        assertThat(redisTemplate.hasKey(key)).isFalse();
        try {
            redisTemplate.opsForValue().set(key, "synthetic", Duration.ofSeconds(30));
            assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("synthetic");
            Long ttl = redisTemplate.getExpire(key);
            assertThat(ttl).isBetween(1L, 30L);
        } finally {
            assertThat(redisTemplate.delete(key)).isTrue();
        }
        assertThat(redisTemplate.hasKey(key)).isFalse();
    }

    @Test
    void bindsThePinnedRealRedisServerIdentity() {
        assertThat(redisTemplate.getConnectionFactory()).isNotNull();
        var connection = redisTemplate.getConnectionFactory().getConnection();
        try {
            assertThat(connection.ping()).isEqualTo("PONG");
            Properties server = connection.serverCommands().info("server");
            assertThat(server).isNotNull();
            assertThat(server.getProperty("redis_version")).isEqualTo("8.4.5");
        } finally {
            connection.close();
        }
        assertThat(redis.imageDigest()).isEqualTo(
                "sha256:efe6e2625e4601cd7119c4fb48b1c04cf3071f8b1729ede1216ceee8bc99742d");
        assertThat(redis.platformImageDigest()).isEqualTo(redis.containerImageDigest());
        assertThat(redis.platform()).isEqualTo(redis.containerPlatform());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class
    })
    static class RedisVerificationApplication {
    }
}
