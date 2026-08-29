package com.ycsopen.sms.core.repository;

import com.ycsopen.sms.core.domain.entity.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    long countByTenantIdAndCreatedAtBetween(Long tenantId, LocalDateTime start, LocalDateTime end);
    long countByChannelIdAndCreatedAtBetween(Long channelId, LocalDateTime start, LocalDateTime end);
}
