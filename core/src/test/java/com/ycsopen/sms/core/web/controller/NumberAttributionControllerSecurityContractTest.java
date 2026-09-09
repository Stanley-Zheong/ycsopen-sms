package com.ycsopen.sms.core.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class NumberAttributionControllerSecurityContractTest {
    @Test
    void controllerMethodsDeclareExactNumberAttributionPermissions() {
        Map<String, String> permissions = Arrays.stream(NumberAttributionController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PreAuthorize.class))
                .collect(Collectors.toMap(method -> method.getName(),
                        method -> method.getAnnotation(PreAuthorize.class).value()));

        assertThat(permissions).containsEntry("versions", "hasAuthority('number-attribution:read')")
                .containsEntry("lookup", "hasAuthority('number-attribution:read')")
                .containsEntry("portabilityRows", "hasAuthority('number-attribution:read')")
                .containsEntry("importPrefixes", "hasAuthority('number-attribution:import')")
                .containsEntry("savePortability", "hasAuthority('number-attribution:portability')");
    }
}
