package com.ycsopen.sms.core.common.security.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafeLogValueTest {

    @Test
    void rendersOnlyBoundedAllowlistedFacts() {
        assertThat(SafeLogValue.correlation("trace-01:attempt.2").toString())
                .isEqualTo("correlation=trace-01:attempt.2");
        assertThat(SafeLogValue.purpose("snapshot.restore").toString())
                .isEqualTo("purpose=snapshot.restore");
        assertThat(SafeLogValue.hashedLocator("A".repeat(64)).toString())
                .isEqualTo("locator_sha256=" + "a".repeat(64));
        assertThat(SafeLogValue.status(TestStatus.REJECTED).toString())
                .isEqualTo("status=REJECTED");
        assertThat(SafeLogValue.count(19).toString()).isEqualTo("count=19");
    }

    @Test
    void rejectsControlInjectionAndNeverEchoesUnsafeInput() {
        String canary = "trace-ok\r\npassword=credential-canary";

        assertThat(SafeLogValue.correlation(canary).toString())
                .isEqualTo("correlation=invalid")
                .doesNotContain("credential-canary", "\r", "\n");
        assertThat(SafeLogValue.purpose("https://objects.example/canary").toString())
                .isEqualTo("purpose=invalid")
                .doesNotContain("objects.example");
        assertThat(SafeLogValue.hashedLocator("raw-store-locator").toString())
                .isEqualTo("locator_sha256=invalid")
                .doesNotContain("raw-store-locator");
        assertThat(SafeLogValue.correlation(null).toString()).isEqualTo("correlation=unavailable");
        assertThatThrownBy(() -> SafeLogValue.count(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private enum TestStatus {
        REJECTED
    }
}
