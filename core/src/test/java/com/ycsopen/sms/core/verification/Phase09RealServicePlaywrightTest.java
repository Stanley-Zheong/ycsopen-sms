package com.ycsopen.sms.core.verification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

/** Real Spring/Vite/MySQL/SoftHSM acceptance for tenant access administration. */
@EnabledIfSystemProperty(named = "phase01.integration.enabled", matches = "true")
class Phase09RealServicePlaywrightTest {
    @Test
    void chromeRunsTenantAccessAcceptanceAgainstRealServices() throws Exception {
        String output = Phase08RealServicePlaywrightTest.runChromeAcceptance("real-smoke-p09");
        assertThat(output).contains("PHASE09_REAL_SERVICE_CHROME_SMOKE_PASS")
                .doesNotContain("Phase09-Valid!123");
        assertThat(Files.readString(Phase01ServiceHarness.repositoryRoot().resolve(
                ".planning/phases/09-tenant-access-administration/EVIDENCE/phase09-playwright-raw.json")))
                .contains("pw-p9-tenant-administrators-create", "pw-p9-tenant-administrators-page")
                .doesNotContain("Phase09-Valid!123");
    }
}
