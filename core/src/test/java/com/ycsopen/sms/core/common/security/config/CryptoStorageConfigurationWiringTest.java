package com.ycsopen.sms.core.common.security.config;

import com.ycsopen.sms.core.common.security.key.lifecycle.FieldReferencePublicationFence;
import com.ycsopen.sms.core.common.security.key.lifecycle.JdbcFieldReferencePublicationFence;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Production-style component-scan proof for the explicit crypto-storage bean graph. */
class CryptoStorageConfigurationWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestApplication.class)
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
            .withBean(PlatformTransactionManager.class,
                    () -> mock(PlatformTransactionManager.class));

    @Test
    void componentScanResolvesExactlyOneFieldPublicationFence() {
        runner.withPropertyValues("ycsopen.security.crypto-storage.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed()
                            .hasSingleBean(FieldReferencePublicationFence.class)
                            .hasBean("fieldReferencePublicationFence")
                            .doesNotHaveBean("jdbcFieldReferencePublicationFence");
                    assertThat(context.getBean(FieldReferencePublicationFence.class))
                            .isExactlyInstanceOf(JdbcFieldReferencePublicationFence.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import(CryptoStorageConfiguration.class)
    @ComponentScan("com.ycsopen.sms.core.common.security.key.lifecycle")
    static class TestApplication {
    }
}
