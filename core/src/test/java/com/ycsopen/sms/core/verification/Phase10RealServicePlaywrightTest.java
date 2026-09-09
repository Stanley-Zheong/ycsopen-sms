package com.ycsopen.sms.core.verification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Phase 10 desktop Chrome acceptance over the real protected service topology. */
@EnabledIfSystemProperty(named = "phase01.integration.enabled", matches = "true")
class Phase10RealServicePlaywrightTest {
    @Test
    void chromeRunsChannelConfigurationAcceptanceAgainstRealServices() throws Exception {
        String output = Phase08RealServicePlaywrightTest.runChromeAcceptance("real-smoke-p10");
        Path root = Phase01ServiceHarness.repositoryRoot();
        String report = Files.readString(root.resolve(
                ".planning/phases/10-channel-configuration-lifecycle/EVIDENCE/phase10-playwright-raw.json"));

        assertThat(output).contains("PHASE10_REAL_SERVICE_CHROME_SMOKE_PASS")
                .doesNotContain("Phase10-Valid!123", "P10-Secret!123");
        assertThat(report).contains("pw-p10-channel-configuration", "pw-p10-channel-offline")
                .doesNotContain("Phase10-Valid!123", "P10-Secret!123");
    }
}
