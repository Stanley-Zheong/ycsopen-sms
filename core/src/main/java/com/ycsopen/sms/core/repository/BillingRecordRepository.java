package com.ycsopen.sms.core.repository;

import com.ycsopen.sms.core.domain.entity.BillingRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BillingRecordRepository extends JpaRepository<BillingRecord, Long> {
    Optional<BillingRecord> findFirstByTaskRefId(Long taskRefId);
}
