package com.ycsopen.sms.core.repository;

import com.ycsopen.sms.core.domain.entity.BillingRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingRecordRepository extends JpaRepository<BillingRecord, Long> {
}
