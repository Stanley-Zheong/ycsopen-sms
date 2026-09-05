package com.ycsopen.sms.core.repository;

import com.ycsopen.sms.core.domain.entity.MessageTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Read/count repository plus the adapter-owned flush primitive. New protected writes must enter
 * through {@code MessageTaskProtectionAdapter}; direct saves cannot construct a protected value.
 */
public interface MessageTaskRepository extends JpaRepository<MessageTask, Long> {
    Optional<MessageTask> findByMessageId(String messageId);

    long countByTenantIdAndCreatedAtBetween(Long tenantId, LocalDateTime start, LocalDateTime end);
    long countByChannelIdAndCreatedAtBetween(Long channelId, LocalDateTime start, LocalDateTime end);
}
