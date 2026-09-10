package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.User;
import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.service.billing.FeeWarningCreditService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeeWarningCreditControllerTest {
    @Test
    void platformEndpointsRequireAdminOrFinanceAndTenantEndpointRequiresTenantRead() throws Exception {
        assertPreAuthorize("rules", "hasAnyRole('ADMIN', 'FINANCE')");
        assertPreAuthorize("saveRule", "hasAnyRole('ADMIN', 'FINANCE')");
        assertPreAuthorize("evaluate", "hasAnyRole('ADMIN', 'FINANCE')");
        assertPreAuthorize("episodes", "hasAnyRole('ADMIN', 'FINANCE')");
        assertPreAuthorize("approve", "hasAnyRole('ADMIN', 'FINANCE')");
        assertPreAuthorize("tenantEpisodes", "hasAuthority('trial-prepaid:read')");
    }

    @Test
    void tenantEpisodesUseAuthenticatedTenantScope() {
        FeeWarningCreditService service = mock(FeeWarningCreditService.class);
        UserRepository users = mock(UserRepository.class);
        User user = new User();
        user.setId(11L);
        user.setTenantId(42L);
        when(users.findById(11L)).thenReturn(Optional.of(user));
        FeeWarningCreditController controller = new FeeWarningCreditController(service, users);

        controller.tenantEpisodes(authentication("11", "ROLE_TENANT_ADMIN"));

        verify(service).episodes(42L);
    }

    @Test
    void tenantEpisodesRejectMissingTenantContextBeforeServiceAccess() {
        FeeWarningCreditController controller = new FeeWarningCreditController(
                mock(FeeWarningCreditService.class), mock(UserRepository.class));

        assertThatThrownBy(() -> controller.tenantEpisodes(authentication("missing", "ROLE_TENANT_ADMIN")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo("AUTHENTICATED_ACTOR_REQUIRED");
    }

    @Test
    void evaluatePassesEstimatedAmountAndActorToService() {
        FeeWarningCreditService service = mock(FeeWarningCreditService.class);
        FeeWarningCreditController controller = new FeeWarningCreditController(service, mock(UserRepository.class));

        controller.evaluate(new FeeWarningCreditController.EvaluationRequest(7L, 50L),
                authentication("finance", "ROLE_FINANCE"));

        verify(service).evaluateTenant(7L, 50L, "finance");
    }

    private static void assertPreAuthorize(String methodName, String expected) throws Exception {
        Method method = List.of(FeeWarningCreditController.class.getDeclaredMethods()).stream()
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        assertThat(method.getAnnotation(PreAuthorize.class).value()).isEqualTo(expected);
    }

    private static TestingAuthenticationToken authentication(String name, String role) {
        return new TestingAuthenticationToken(name, "n/a", List.of(new SimpleGrantedAuthority(role)));
    }
}
