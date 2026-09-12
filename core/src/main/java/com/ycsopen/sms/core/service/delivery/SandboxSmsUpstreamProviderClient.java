package com.ycsopen.sms.core.service.delivery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Default deterministic upstream for local development and automated tests. */
@Component
@ConditionalOnProperty(
        prefix = "ycsopen.sms.upstream-http",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true)
public class SandboxSmsUpstreamProviderClient implements SmsUpstreamProviderClient {
    @Override
    public ProviderSubmitResult submit(ProviderSubmitRequest request) {
        return ProviderSubmitResult.accepted("SANDBOX-" + request.messageId());
    }
}
