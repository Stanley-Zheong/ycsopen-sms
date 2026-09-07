package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.service.configuration.PlatformConfigurationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@SpringJUnitConfig(PlatformConfigurationControllerTest.TestConfig.class)
class PlatformConfigurationControllerTest {
    @Autowired PlatformConfigurationController controller;
    @Autowired PlatformConfigurationService service;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readRequiresTheCurrentReadPermission() {
        authenticate("7", "ROLE_OPERATOR");
        assertThatThrownBy(controller::view).isInstanceOf(AccessDeniedException.class);

        authenticate("7", "ROLE_OPERATOR", "system:configuration:read");
        controller.view();
        verify(service).view();
    }

    @Test
    void writeAndActivateUseSeparatePermissionsAndAttributeActor() {
        authenticate("7", "ROLE_OPERATOR", "system:configuration:write");
        var stage = new PlatformConfigurationController.StageRequest(
                0, Map.of("security.login.max-failures", "8"), "reason");
        controller.stage(stage, SecurityContextHolder.getContext().getAuthentication());
        verify(service).stage(new PlatformConfigurationService.StageCommand(
                0, stage.changes(), "reason", 7));

        assertThatThrownBy(() -> controller.activate(3,
                new PlatformConfigurationController.ActivationRequest(0, "activate"),
                SecurityContextHolder.getContext().getAuthentication()))
                .isInstanceOf(AccessDeniedException.class);

        authenticate("7", "ROLE_OPERATOR", "system:configuration:activate");
        controller.activate(3, new PlatformConfigurationController.ActivationRequest(0, "activate"),
                SecurityContextHolder.getContext().getAuthentication());
        verify(service).activate(new PlatformConfigurationService.ActivateCommand(3, 0, "activate", 7));
    }

    @Test
    void failuresExposeAStableMachineCodeInsteadOfTranslatedCopy() {
        var response = controller.failure(new PlatformConfigurationService.Failure(
                PlatformConfigurationService.FailureCode.RELOAD_REJECTED));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().errorCode()).isEqualTo("RELOAD_REJECTED");
    }

    private static void authenticate(String subject, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(subject, null,
                        java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    @Configuration
    @EnableMethodSecurity
    static class TestConfig {
        @Bean PlatformConfigurationService service() {
            return mock(PlatformConfigurationService.class);
        }

        @Bean PlatformConfigurationController controller(PlatformConfigurationService service) {
            return new PlatformConfigurationController(service);
        }
    }
}
