package com.ycsopen.sms.core.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class FrequencyRuleControllerSecurityContractTest {
    @Test
    void controllerMethodsDeclareExactFrequencyPermissions() {
        Map<String, String> permissions = Arrays.stream(FrequencyRuleController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PreAuthorize.class))
                .collect(Collectors.toMap(method -> method.getName(),
                        method -> method.getAnnotation(PreAuthorize.class).value()));

        assertThat(permissions).containsEntry("rules", "hasAuthority('frequency:read')")
                .containsEntry("analytics", "hasAuthority('frequency:read')")
                .containsEntry("save", "hasAuthority('frequency:write')")
                .containsEntry("enable", "hasAuthority('frequency:write')")
                .containsEntry("disable", "hasAuthority('frequency:write')")
                .containsEntry("importRules", "hasAuthority('frequency:import')")
                .containsEntry("exportRequest", "hasAuthority('frequency:export')");
    }
}
