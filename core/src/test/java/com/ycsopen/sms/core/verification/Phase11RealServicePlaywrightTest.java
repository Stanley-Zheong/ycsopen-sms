package com.ycsopen.sms.core.verification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Phase 11 desktop Chrome acceptance over the real protected service topology. */
@EnabledIfSystemProperty(named = "phase01.integration.enabled", matches = "true")
class Phase11RealServicePlaywrightTest {
    @Test
    void chromeRunsChannelHealthAndPoolsAcceptanceAgainstRealServices() throws Exception {
        String output = Phase08RealServicePlaywrightTest.runChromeAcceptance("real-smoke-p11");
        Path root = Phase01ServiceHarness.repositoryRoot();
        String report = Files.readString(root.resolve(
                ".planning/phases/11-channel-health-pools-candidate-pause/EVIDENCE/phase11-playwright-raw.json"));

        assertThat(output).contains("PHASE11_REAL_SERVICE_CHROME_SMOKE_PASS")
                .doesNotContain("Phase11-Valid!123");
        assertThat(report).contains("pw-p11-channel-monitor", "pw-p11-channel-pause",
                        "pw-p11-channel-pools", "pw-p11-pool-weight-editor")
                .doesNotContain("Phase11-Valid!123");
    }
}
