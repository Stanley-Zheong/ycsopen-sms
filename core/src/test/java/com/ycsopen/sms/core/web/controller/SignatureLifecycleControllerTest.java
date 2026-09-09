package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.User;
import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.service.signature.SignatureLifecycleService;
import com.ycsopen.sms.core.web.dto.SignatureApplicationRequest;
import com.ycsopen.sms.core.web.dto.SignatureResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SignatureLifecycleControllerTest {

    @Test
    void tenantEndpointsResolveTenantIdFromCurrentUserRecordNotPrincipalSubject() {
        SignatureLifecycleService service = mock(SignatureLifecycleService.class);
        UserRepository users = mock(UserRepository.class);
        User user = new User();
        user.setId(7L);
        user.setTenantId(42L);
        when(users.findById(7L)).thenReturn(Optional.of(user));
        SignatureLifecycleController controller = new SignatureLifecycleController(service, users);

        controller.tenantList(auth("7"));
        controller.submit(new SignatureApplicationRequest("优创", "ENTERPRISE", "SELF", null, "申请人", null), auth("7"));

        verify(service).listTenant(42L);
        verify(service).submitApplication(eq(42L), any(SignatureApplicationRequest.class));
        verify(service, never()).listTenant(7L);
    }

    @Test
    void usableChannelsRejectsCrossTenantSignature() {
        SignatureLifecycleService service = mock(SignatureLifecycleService.class);
        UserRepository users = mock(UserRepository.class);
        User user = new User();
        user.setId(7L);
        user.setTenantId(42L);
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(service.get(1201L)).thenReturn(signature(1201L, 43L));
        SignatureLifecycleController controller = new SignatureLifecycleController(service, users);

        assertThatThrownBy(() -> controller.usableChannels(1201L, auth("7")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("SIGNATURE_FORBIDDEN"));
        verify(service, never()).usableChannels(1201L);
    }

    private static UsernamePasswordAuthenticationToken auth(String subject) {
        return UsernamePasswordAuthenticationToken.authenticated(subject, null, List.of());
    }

    private static SignatureResponse signature(long id, long tenantId) {
        return new SignatureResponse(id, tenantId, "SGN", "优创", "ENTERPRISE", "SELF", "LOW", null,
                "申请人", "APPROVED", "", null, LocalDateTime.now(), List.of());
    }
}
