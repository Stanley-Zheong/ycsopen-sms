package com.ycsopen.sms.core.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseRuntimeConfigurationTest {

    @Test
    void releaseIncludesTheRealActuatorHealthAndInfoRuntime() {
        assertThat(ClassUtils.isPresent(
                "org.springframework.boot.actuate.health.HealthEndpoint",
                getClass().getClassLoader()))
                .as("removing the actuator dependency breaks Compose health and build identity")
                .isTrue();
    }

    @Test
    void releaseDisablesFlywayTemplateExpansionAndPublishesBuildIdentity() throws Exception {
        var sources = new MutablePropertySources();
        var loaded = new YamlPropertySourceLoader()
                .load("release-runtime", new ClassPathResource("application.yml"));
        loaded.forEach(sources::addLast);
        var properties = new PropertySourcesPropertyResolver(sources);

        assertThat(properties.getProperty("spring.flyway.placeholder-replacement", Boolean.class))
                .as("Flyway must preserve message-domain ${var} literals in V1")
                .isFalse();
        assertThat(properties.getProperty("management.info.env.enabled", Boolean.class))
                .as("/actuator/info must publish configured build information")
                .isTrue();
        assertThat(loaded.getFirst().getProperty("info.build.commit"))
                .isEqualTo("${BUILD_COMMIT:unknown}");
    }

    @Test
    void developmentReleaseKeepsThePreIssue60BaselineSeparatelyAddressable() throws Exception {
        var loaded = new YamlPropertySourceLoader()
                .load("development-release", new ClassPathResource("application-dev.yml"));

        assertThat(loaded.getFirst().getProperty("spring.flyway.locations"))
                .isEqualTo("classpath:db/migration,classpath:db/devmigration,classpath:db/releasemigration");
    }
}
