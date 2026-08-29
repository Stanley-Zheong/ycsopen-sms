package com.ycsopen.sms.core.repository;

import com.ycsopen.sms.core.domain.entity.MessageTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface MessageTaskRepository extends JpaRepository<MessageTask, Long> {
    Optional<MessageTask> findByMessageId(String messageId);

    long countByTenantIdAndCreatedAtBetween(Long tenantId, LocalDateTime start, LocalDateTime end);
    long countByChannelIdAndCreatedAtBetween(Long channelId, LocalDateTime start, LocalDateTime end);
}
