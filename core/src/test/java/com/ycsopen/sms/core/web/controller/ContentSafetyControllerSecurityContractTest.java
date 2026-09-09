package com.ycsopen.sms.core.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContentSafetyControllerSecurityContractTest {

    @Test
    void everyEndpointRequiresTheSpecificContentSafetyAuthority() throws Exception {
        Map<String, String> expected = Map.of(
                "policies", "content-safety:read",
                "save", "content-safety:write",
                "importPolicies", "content-safety:import",
                "delete", "content-safety:write",
                "exportRequest", "content-safety:export",
                "analytics", "content-safety:read",
                "scan", "content-safety:scan"
        );

        for (Map.Entry<String, String> entry : expected.entrySet()) {
            Method method = java.util.Arrays.stream(ContentSafetyController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(entry.getKey()))
                    .findFirst()
                    .orElseThrow();
            PreAuthorize guard = method.getAnnotation(PreAuthorize.class);

            assertThat(guard).as(entry.getKey()).isNotNull();
            assertThat(guard.value()).as(entry.getKey()).contains(entry.getValue());
        }
    }
}
