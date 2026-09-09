package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.RateLimitExceededException;
import com.ycsopen.sms.core.service.message.MessageSubmitService;
import com.ycsopen.sms.core.service.routing.ApiKeyRateLimitService;
import com.ycsopen.sms.core.service.routing.ApiKeyRateLimitService.RatePolicy;
import com.ycsopen.sms.core.web.dto.SmsSendRequest;
import com.ycsopen.sms.core.web.interceptor.HmacAuthInterceptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MessageControllerRateLimitTest {
    @Mock MessageSubmitService submitService;
    @Mock ApiKeyRateLimitService rateLimitService;

    @Test
    void exceededApiKeyRateLimitStopsBeforeTaskOrChargeSubmission() {
        MockHttpServletRequest http = new MockHttpServletRequest();
        http.setRemoteAddr("127.0.0.1");
        http.setAttribute(HmacAuthInterceptor.ATTR_TENANT_ID, 17L);
        http.setAttribute(HmacAuthInterceptor.ATTR_API_KEY_ID, 19L);
        RatePolicy policy = new RatePolicy(1, 60, 3600, 86400);
        http.setAttribute(HmacAuthInterceptor.ATTR_RATE_POLICY, policy);
        SmsSendRequest request = new SmsSendRequest("SUBMIT-1", "13900000001", "tpl-1", "sign-1", Map.of(), null);
        doThrow(new RateLimitExceededException("api-key-SECOND", 1))
                .when(rateLimitService).enforce(17L, 19L, policy);

        assertThatThrownBy(() -> new MessageController(submitService, rateLimitService).send(request, http))
                .isInstanceOf(RateLimitExceededException.class);

        verify(rateLimitService).enforce(17L, 19L, policy);
        verifyNoInteractions(submitService);
    }
}
