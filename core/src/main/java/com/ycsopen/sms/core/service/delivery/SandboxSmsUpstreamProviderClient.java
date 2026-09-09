package com.ycsopen.sms.core.service.delivery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/** Default deterministic upstream for local development and automated tests. */
@Component
@ConditionalOnMissingBean(SmsUpstreamProviderClient.class)
public class SandboxSmsUpstreamProviderClient implements SmsUpstreamProviderClient {
    @Override
    public ProviderSubmitResult submit(ProviderSubmitRequest request) {
        return ProviderSubmitResult.accepted("SANDBOX-" + request.messageId());
    }
}
