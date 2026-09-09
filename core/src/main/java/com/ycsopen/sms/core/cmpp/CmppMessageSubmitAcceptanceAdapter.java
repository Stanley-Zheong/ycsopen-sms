package com.ycsopen.sms.core.cmpp;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.service.message.MessageSubmitService;
import com.ycsopen.sms.core.web.dto.SmsSendRequest;
import org.springframework.stereotype.Service;

import java.util.Map;

/** Adapts downstream CMPP SUBMIT into the same acceptance service used by HTTP ingress. */
@Service
public class CmppMessageSubmitAcceptanceAdapter implements CmppDownstreamAcceptancePort {
    private final MessageSubmitService messageSubmitService;

    public CmppMessageSubmitAcceptanceAdapter(MessageSubmitService messageSubmitService) {
        this.messageSubmitService = messageSubmitService;
    }

    @Override
    public AcceptanceResult accept(SubmitCommand command) {
        if (blank(command.serviceId()) || blank(command.productCode())) {
            return AcceptanceResult.rejected("CMPP_BINDING_REQUIRED", "CMPP service and product binding are required");
        }
        try {
            var response = messageSubmitService.submit(command.tenantId(), new SmsSendRequest(
                    command.submitId(), command.destination(), command.templateId(), null,
                    Map.of("content", command.content(),
                            "_cmpp_service_id", command.serviceId(),
                            "_cmpp_product_code", command.productCode(),
                            "_cmpp_credential_id", String.valueOf(command.credentialId())), null), command.clientIp());
            return AcceptanceResult.accepted(response.messageId());
        } catch (BusinessException failure) {
            return AcceptanceResult.rejected(failure.getErrorCode(), failure.getMessage());
        } catch (IllegalArgumentException failure) {
            return AcceptanceResult.rejected("CMPP_SUBMIT_INVALID", failure.getMessage());
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
