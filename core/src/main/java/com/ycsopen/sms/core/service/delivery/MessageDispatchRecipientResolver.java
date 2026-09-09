package com.ycsopen.sms.core.service.delivery;

public interface MessageDispatchRecipientResolver {
    String requireRecipient(long taskId, long tenantId, String messageId);
}
