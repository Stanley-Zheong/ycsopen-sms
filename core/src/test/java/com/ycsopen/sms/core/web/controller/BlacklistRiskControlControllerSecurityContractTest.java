package com.ycsopen.sms.core.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BlacklistRiskControlControllerSecurityContractTest {

    @Test
    void everyMutatingEndpointRequiresItsSpecificAuthority() throws Exception {
        Map<String, String> expected = Map.of(
                "create", "blacklist:write",
                "importEntries", "blacklist:import",
                "disable", "blacklist:write",
                "exportRequest", "blacklist:export",
                "saveProvider", "risk-provider:write",
                "check", "risk-analysis:check",
                "appeal", "risk-analysis:appeal"
        );

        for (Map.Entry<String, String> entry : expected.entrySet()) {
            Method method = java.util.Arrays.stream(BlacklistRiskControlController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(entry.getKey()))
                    .findFirst()
                    .orElseThrow();
            PreAuthorize guard = method.getAnnotation(PreAuthorize.class);

            assertThat(guard).as(entry.getKey()).isNotNull();
            assertThat(guard.value()).as(entry.getKey()).contains(entry.getValue());
        }
    }
}
