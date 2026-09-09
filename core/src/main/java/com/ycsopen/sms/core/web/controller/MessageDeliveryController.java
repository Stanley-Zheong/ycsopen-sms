package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.service.delivery.HttpMessageDeliveryService;
import com.ycsopen.sms.core.service.delivery.MessageStatusQueryService;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import com.ycsopen.sms.core.web.dto.delivery.ProviderReceiptRequest;
import com.ycsopen.sms.core.web.interceptor.HmacAuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sms")
public class MessageDeliveryController {
    private final MessageStatusQueryService statuses;
    private final HttpMessageDeliveryService delivery;

    public MessageDeliveryController(MessageStatusQueryService statuses,
                                     HttpMessageDeliveryService delivery) {
        this.statuses = statuses;
        this.delivery = delivery;
    }

    @GetMapping("/status/{messageId}")
    public ApiResponse<MessageStatusQueryService.MessageStatusDetail> status(@PathVariable String messageId,
                                                                              HttpServletRequest request) {
        Long tenantId = (Long) request.getAttribute(HmacAuthInterceptor.ATTR_TENANT_ID);
        return ApiResponse.ok(statuses.status(tenantId, messageId));
    }

    @PostMapping("/receipts")
    public ApiResponse<HttpMessageDeliveryService.ReceiptResult> receipt(@RequestBody ProviderReceiptRequest request) {
        return ApiResponse.ok(delivery.applyReceipt(new HttpMessageDeliveryService.ReceiptCommand(
                request.messageId(), request.providerMessageId(), request.channelId(), request.providerStatus(),
                request.errorCode(), request.errorMessage(), request.safePayload(), request.reportTime())));
    }
}
