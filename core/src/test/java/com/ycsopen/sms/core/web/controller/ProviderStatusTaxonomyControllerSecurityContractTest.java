package com.ycsopen.sms.core.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderStatusTaxonomyControllerSecurityContractTest {
    @Test
    void controllerMethodsDeclareExactProviderStatusPermissions() {
        Map<String, String> permissions = Arrays.stream(ProviderStatusTaxonomyController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PreAuthorize.class))
                .collect(Collectors.toMap(method -> method.getName(),
                        method -> method.getAnnotation(PreAuthorize.class).value()));

        assertThat(permissions).containsEntry("versions", "hasAuthority('provider-status:read')")
                .containsEntry("mappings", "hasAuthority('provider-status:read')")
                .containsEntry("normalize", "hasAuthority('provider-status:read')")
                .containsEntry("importMappings", "hasAuthority('provider-status:import')")
                .containsEntry("exportRequest", "hasAuthority('provider-status:export')");
    }
}
