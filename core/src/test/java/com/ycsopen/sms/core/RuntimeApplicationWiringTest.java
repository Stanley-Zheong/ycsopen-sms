package com.ycsopen.sms.core;

import com.ycsopen.sms.core.service.configuration.PlatformConfigurationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(
        classes = YcsopenSmsCoreApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "management.health.redis.enabled=false",
                "spring.quartz.auto-startup=false",
                "spring.task.scheduling.enabled=false",
                "ycsopen.database.runtime-grants.enabled=false"
        })
class RuntimeApplicationWiringTest {

    // Its @PostConstruct reads Flyway-owned non-entity tables; Docker acceptance covers that initializer.
    @MockBean
    private PlatformConfigurationService platformConfigurationService;

    @Test
    void completeRuntimeBeanGraphStarts() {
    }
}
