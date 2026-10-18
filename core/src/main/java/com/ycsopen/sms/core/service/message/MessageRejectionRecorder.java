package com.ycsopen.sms.core.service.message;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Commits a controlled HTTP submission rejection after the business transaction has rolled back. */
@Service
public class MessageRejectionRecorder {
    private final MessageAcceptanceIdempotencyService idempotency;

    public MessageRejectionRecorder(MessageAcceptanceIdempotencyService idempotency) {
        this.idempotency = idempotency;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(MessageAcceptanceIdempotencyService.Claim claim,
                       Long templateId,
                       Long signatureId,
                       String errorCode) {
        idempotency.recordRejected(claim, templateId, signatureId, errorCode);
    }
}
