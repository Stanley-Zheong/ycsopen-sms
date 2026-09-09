package com.ycsopen.sms.core.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingCircuitPolicyControllerSecurityContractTest {
    @Test
    void controllerMethodsDeclareExactRoutingPolicyPermissions() {
        Map<String, String> permissions = Arrays.stream(RoutingCircuitPolicyController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PreAuthorize.class))
                .collect(Collectors.toMap(method -> method.getName() + "#" + method.getParameterCount(),
                        method -> method.getAnnotation(PreAuthorize.class).value()));

        assertThat(permissions).containsEntry("versions#0", "hasAuthority('routing-policy:read')")
                .containsEntry("rules#0", "hasAuthority('routing-policy:read')")
                .containsEntry("importPolicy#2", "hasAuthority('routing-policy:import')")
                .containsEntry("simulate#1", "hasAuthority('routing-policy:read')")
                .containsEntry("circuits#0", "hasAuthority('routing-policy:read')")
                .containsEntry("recordCircuit#3", "hasAuthority('routing-policy:write')")
                .containsEntry("retryPolicy#1", "hasAuthority('routing-policy:read')")
                .containsEntry("saveRetryPolicy#1", "hasAuthority('routing-policy:write')");
    }
}
