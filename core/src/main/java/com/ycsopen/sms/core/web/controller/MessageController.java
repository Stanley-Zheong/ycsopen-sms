package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.service.message.MessageSubmitService;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import com.ycsopen.sms.core.web.dto.SmsSendRequest;
import com.ycsopen.sms.core.web.dto.SmsSendResponse;
import com.ycsopen.sms.core.web.interceptor.HmacAuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** F-6.1 HTTP API 单条发送 —— 下游机构真正调用的入口。鉴权见 HmacAuthInterceptor。 */
@RestController
@RequestMapping("/api/v1/sms")
public class MessageController {

    private final MessageSubmitService messageSubmitService;

    public MessageController(MessageSubmitService messageSubmitService) {
        this.messageSubmitService = messageSubmitService;
    }

    @PostMapping("/send")
    public ApiResponse<SmsSendResponse> send(@Valid @RequestBody SmsSendRequest request, HttpServletRequest httpRequest) {
        Long tenantId = (Long) httpRequest.getAttribute(HmacAuthInterceptor.ATTR_TENANT_ID);
        return ApiResponse.ok(messageSubmitService.submit(tenantId, request, httpRequest.getRemoteAddr()));
    }
}
