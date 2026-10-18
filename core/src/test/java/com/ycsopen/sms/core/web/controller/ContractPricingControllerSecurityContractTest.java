package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.User;
import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.service.billing.ContractPricingService;
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

import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(ContractPricingControllerSecurityContractTest.Config.class)
class ContractPricingControllerSecurityContractTest {
    @Autowired ContractPricingService service;
    @Autowired UserRepository users;
    @Autowired ContractPricingController controller;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void workbenchRequiresReadAuthorityAndPlatformRole() {
        authenticate("101", "ROLE_OPERATOR");
        assertThatThrownBy(() -> controller.workbench(null, null, null, null, authentication()))
                .isInstanceOf(AccessDeniedException.class);

        authenticate("101", "ROLE_TENANT", "trial-prepaid:read");
        assertThatThrownBy(() -> controller.workbench(null, null, null, null, authentication()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅平台账号可执行此操作");

        authenticate("101", "ROLE_OPERATOR", "trial-prepaid:read");
        controller.workbench("Acme", "Alice", "SaaS", "TRIAL", authentication());
        verify(service).workbench(any(ContractPricingService.WorkbenchQuery.class));
    }

    @Test
    void analysisAndPriceBooksRequireReadAuthorityAndPlatformRole() {
        authenticate("101", "ROLE_OPERATOR");
        assertThatThrownBy(() -> controller.analysis(7, authentication()))
                .isInstanceOf(AccessDeniedException.class);

        authenticate("101", "ROLE_TENANT", "trial-prepaid:read");
        assertThatThrownBy(() -> controller.priceBooks(authentication()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅平台账号可执行此操作");
    }

    @Test
    void conversionRequiresWriteAuthorityAndPlatformRole() {
        authenticate("101", "ROLE_OPERATOR", "trial-prepaid:read");
        assertThatThrownBy(() -> controller.approve(7, null, authentication()))
                .isInstanceOf(AccessDeniedException.class);

        authenticate("101", "ROLE_TENANT", "trial-prepaid:write");
        assertThatThrownBy(() -> controller.approve(7, null, authentication()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅平台账号可执行此操作");

        authenticate("101", "ROLE_FINANCE", "trial-prepaid:write");
        controller.approve(7, null, authentication());
        verify(service).approveContract(7, null, "101");
    }

    @Test
    void tenantOverviewAllowsOwnTenantAndRejectsAnotherTenant() {
        User tenantUser = new User();
        tenantUser.setTenantId(42L);
        when(users.findById(7L)).thenReturn(Optional.of(tenantUser));
        authenticate("7", "ROLE_TENANT_ADMIN", "trial-prepaid:read");

        controller.overview(42, authentication());
        verify(service).overview(42);
        assertThatThrownBy(() -> controller.overview(99, authentication()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能访问其他机构合同");
    }

    private static void authenticate(String subject, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(subject, null,
                        Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    private static org.springframework.security.core.Authentication authentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean ContractPricingService service() {
            return mock(ContractPricingService.class);
        }

        @Bean UserRepository users() {
            return mock(UserRepository.class);
        }

        @Bean ContractPricingController controller(ContractPricingService service, UserRepository users) {
            return new ContractPricingController(service, users);
        }
    }
}
