package com.ycsopen.sms.core.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.User;
import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.service.billing.TrialPrepaidLedgerService;

class TrialPrepaidLedgerControllerSecurityContractTest {
    @Test
    void controllerMethodsDeclareExactTrialPrepaidPermissions() {
        Map<String, String> permissions = Arrays.stream(TrialPrepaidLedgerController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PreAuthorize.class))
                .collect(Collectors.toMap(method -> method.getName() + "#" + method.getParameterCount(),
                        method -> method.getAnnotation(PreAuthorize.class).value()));

        assertThat(permissions).containsEntry("overview#2", "hasAuthority('trial-prepaid:read')")
                .containsEntry("activateTrial#3", "hasAuthority('trial-prepaid:write')")
                .containsEntry("consumeTrial#3", "hasAuthority('trial-prepaid:write')")
                .containsEntry("conversion#2", "hasAuthority('trial-prepaid:write')")
                .containsEntry("reserve#3", "hasAuthority('trial-prepaid:write')")
                .containsEntry("confirm#3", "hasAuthority('trial-prepaid:write')")
                .containsEntry("reverse#2", "hasAuthority('trial-prepaid:write')")
                .containsEntry("consumption#3", "hasAuthority('trial-prepaid:read')")
                .containsEntry("audits#2", "hasAuthority('trial-prepaid:read')");
        assertThat(permissions.keySet()).noneMatch(name -> name.startsWith("creditForTest#"));
    }

    @Test
    void tenantReadIsScopedToOwnTenant() {
        TrialPrepaidLedgerService service = mock(TrialPrepaidLedgerService.class);
        UserRepository users = mock(UserRepository.class);
        User tenantUser = new User();
        tenantUser.setTenantId(42L);
        when(users.findById(7L)).thenReturn(Optional.of(tenantUser));
        TrialPrepaidLedgerController controller = new TrialPrepaidLedgerController(service, users);

        controller.consumption(null, "SMS", auth("7", "ROLE_TENANT_ADMIN", "trial-prepaid:read"));

        verify(service).consumption(eq(42L), eq("SMS"));
        assertThatThrownBy(() -> controller.overview(99, auth("7", "ROLE_TENANT_ADMIN", "trial-prepaid:read")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能访问其他机构账本");
    }

    @Test
    void tenantCannotUsePlatformOnlyFinancialMutations() {
        TrialPrepaidLedgerController controller = new TrialPrepaidLedgerController(mock(TrialPrepaidLedgerService.class), mock(UserRepository.class));

        assertThatThrownBy(() -> controller.reserve(42, new TrialPrepaidLedgerController.PrepaidReserveRequest(
                "DOC-1", "SMS", "CH", 100, 1), auth("7", "ROLE_TENANT_ADMIN", "trial-prepaid:write")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅平台账号可执行此操作");
    }

    private UsernamePasswordAuthenticationToken auth(String name, String... authorities) {
        return UsernamePasswordAuthenticationToken.authenticated(name, null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }
}
