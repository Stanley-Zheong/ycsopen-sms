package com.ycsopen.sms.core.repository;

import com.ycsopen.sms.core.domain.entity.FrequencyRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FrequencyRuleRepository extends JpaRepository<FrequencyRule, Long> {
    List<FrequencyRule> findAllByStatus(FrequencyRule.Status status);
}
