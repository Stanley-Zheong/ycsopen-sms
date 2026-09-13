package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.service.message.MessageSubmitService;
import com.ycsopen.sms.core.service.routing.ApiKeyRateLimitService;
import com.ycsopen.sms.core.service.routing.ApiKeyRateLimitService.RatePolicy;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import com.ycsopen.sms.core.web.dto.SmsSendRequest;
import com.ycsopen.sms.core.web.dto.SmsSendResponse;
import com.ycsopen.sms.core.web.dto.SmsBatchSendRequest;
import com.ycsopen.sms.core.web.dto.SmsBatchSendResponse;
import com.ycsopen.sms.core.web.interceptor.HmacAuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;

/** F-6.1 HTTP API 单条发送 —— 下游机构真正调用的入口。鉴权见 HmacAuthInterceptor。 */
@RestController
@RequestMapping("/api/v1/sms")
public class MessageController {

    private final MessageSubmitService messageSubmitService;
    private final ApiKeyRateLimitService apiKeyRateLimitService;

    public MessageController(MessageSubmitService messageSubmitService,
                             ApiKeyRateLimitService apiKeyRateLimitService) {
        this.messageSubmitService = messageSubmitService;
        this.apiKeyRateLimitService = apiKeyRateLimitService;
    }

    @PostMapping("/send")
    public ApiResponse<SmsSendResponse> send(@Valid @RequestBody SmsSendRequest request, HttpServletRequest httpRequest) {
        Long tenantId = (Long) httpRequest.getAttribute(HmacAuthInterceptor.ATTR_TENANT_ID);
        Long apiKeyId = (Long) httpRequest.getAttribute(HmacAuthInterceptor.ATTR_API_KEY_ID);
        RatePolicy ratePolicy = (RatePolicy) httpRequest.getAttribute(HmacAuthInterceptor.ATTR_RATE_POLICY);
        apiKeyRateLimitService.enforce(tenantId, apiKeyId, ratePolicy);
        return ApiResponse.ok(messageSubmitService.submit(tenantId, apiKeyId, request, httpRequest.getRemoteAddr()));
    }

    /** External batch endpoint; preserves per-item acceptance/error semantics under the same HMAC identity. */
    @PostMapping("/send/batch")
    public ApiResponse<SmsBatchSendResponse> sendBatch(@Valid @RequestBody SmsBatchSendRequest request,
                                                        HttpServletRequest httpRequest) {
        Long tenantId = (Long) httpRequest.getAttribute(HmacAuthInterceptor.ATTR_TENANT_ID);
        Long apiKeyId = (Long) httpRequest.getAttribute(HmacAuthInterceptor.ATTR_API_KEY_ID);
        RatePolicy ratePolicy = (RatePolicy) httpRequest.getAttribute(HmacAuthInterceptor.ATTR_RATE_POLICY);
        if (new HashSet<>(request.messages().stream().map(SmsSendRequest::submitId).toList()).size() != request.messages().size()) {
            throw new BusinessException("BATCH_SUBMIT_ID_DUPLICATE", "批量请求内 submitId 不能重复");
        }
        apiKeyRateLimitService.enforce(tenantId, apiKeyId, ratePolicy);
        List<SmsBatchSendResponse.Item> items = request.messages().stream().map(item -> {
            try {
                SmsSendResponse result = messageSubmitService.submit(tenantId, apiKeyId, item, httpRequest.getRemoteAddr());
                return new SmsBatchSendResponse.Item(item.submitId(), result.messageId(), result.status(), null);
            } catch (RuntimeException failure) {
                return new SmsBatchSendResponse.Item(item.submitId(), null, "REJECTED", failure.getMessage());
            }
        }).toList();
        return ApiResponse.ok(new SmsBatchSendResponse(items));
    }
}
