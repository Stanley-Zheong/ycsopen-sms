package com.ycsopen.sms.core.cmpp;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.service.delivery.SmsUpstreamProviderClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Adapts the CMPP client core to the existing upstream provider SPI. */
public final class CmppSmsUpstreamProviderClient implements SmsUpstreamProviderClient {
    private static final Logger log = LoggerFactory.getLogger(CmppSmsUpstreamProviderClient.class);

    private final CmppClientSession session;
    private final CmppGateway gateway;

    public CmppSmsUpstreamProviderClient(CmppClientSession session, CmppGateway gateway) {
        this.session = session;
        this.gateway = gateway;
    }

    @Override
    public ProviderSubmitResult submit(ProviderSubmitRequest request) {
        try {
            CmppClientSession.SubmitResult result = session.submit(gateway,
                    new CmppClientSession.SubmitCommand(request.idempotencyKey(), request.messageId(),
                            request.recipient(), request.content(), true));
            if ("ACCEPTED".equals(result.status())) {
                return ProviderSubmitResult.accepted(result.providerMessageId());
            }
            return ProviderSubmitResult.rejected(result.errorCode(), "CMPP rejected");
        } catch (IllegalArgumentException failure) {
            log.warn("Rejecting invalid CMPP submit request: {}", failure.getMessage());
            return ProviderSubmitResult.rejected("CMPP_INVALID_REQUEST", failure.getMessage());
        } catch (BusinessException failure) {
            log.warn("CMPP submit outcome is unknown: code={}, message={}",
                    failure.getErrorCode(), failure.getMessage());
            return ProviderSubmitResult.unknown(failure.getErrorCode(), failure.getMessage());
        } catch (RuntimeException failure) {
            log.error("Unexpected CMPP submit failure", failure);
            return ProviderSubmitResult.unknown("CMPP_UNKNOWN", "CMPP outcome is unknown");
        }
    }
}
