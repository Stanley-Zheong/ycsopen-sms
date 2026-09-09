package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.User;
import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.service.message.MessageSubmitService;
import com.ycsopen.sms.core.web.dto.SmsSendRequest;
import com.ycsopen.sms.core.web.dto.SmsSendResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantConsoleSendControllerTest {
    @Test
    void delegatesJwtTenantSendToAcceptancePipelineWithoutApiKey() {
        MessageSubmitService messages = mock(MessageSubmitService.class);
        UserRepository users = mock(UserRepository.class);
        User user = new User();
        user.setId(81L);
        user.setTenantId(42L);
        when(users.findById(81L)).thenReturn(Optional.of(user));
        SmsSendRequest request = new SmsSendRequest("CONSOLE-1-0", "13800138000", "8", "9",
                Map.of("code", "2468"), null);
        when(messages.submit(42L, null, request, "127.0.0.1"))
                .thenReturn(new SmsSendResponse("MSG_1", "PENDING"));

        var response = new TenantConsoleSendController(messages, users).send(request,
                new TestingAuthenticationToken("81", "n/a"),
                new MockHttpServletRequest("POST", "/api/v1/console/tenant/send"));

        assertThat(response.getData().messageId()).isEqualTo("MSG_1");
        verify(messages).submit(42L, null, request, "127.0.0.1");
    }

    @Test
    void rejectsConsoleSendWithoutTenantContext() {
        UserRepository users = mock(UserRepository.class);
        User platform = new User();
        platform.setId(7L);
        when(users.findById(7L)).thenReturn(Optional.of(platform));

        assertThatThrownBy(() -> new TenantConsoleSendController(mock(MessageSubmitService.class), users)
                .send(new SmsSendRequest("CONSOLE-1", "13800138000", "8", "9", Map.of(), null),
                        new TestingAuthenticationToken("7", "n/a"), new MockHttpServletRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo("TENANT_CONTEXT_REQUIRED");
    }
}
