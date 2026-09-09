package com.ycsopen.sms.core.service.delivery;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.common.security.persistence.MessageTaskProtectionAdapter;
import com.ycsopen.sms.core.domain.entity.MessageTask;
import com.ycsopen.sms.core.repository.MessageTaskRepository;
import org.springframework.stereotype.Component;

@Component
public class ProtectedMessageDispatchRecipientResolver implements MessageDispatchRecipientResolver {
    private final MessageTaskRepository tasks;
    private final MessageTaskProtectionAdapter protection;

    public ProtectedMessageDispatchRecipientResolver(MessageTaskRepository tasks,
                                                     MessageTaskProtectionAdapter protection) {
        this.tasks = tasks;
        this.protection = protection;
    }

    @Override
    public String requireRecipient(long taskId, long tenantId, String messageId) {
        MessageTask task = tasks.findById(taskId)
                .orElseThrow(() -> new BusinessException("MESSAGE_TASK_NOT_FOUND", "消息任务不存在"));
        if (!Long.valueOf(tenantId).equals(task.getTenantId()) || !messageId.equals(task.getMessageId())) {
            throw new BusinessException("MESSAGE_TASK_MISMATCH", "消息任务归属不匹配");
        }
        return protection.revealMobileForDispatch(task);
    }
}
