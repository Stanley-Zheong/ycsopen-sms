package com.ycsopen.sms.core.repository;

import com.ycsopen.sms.core.domain.entity.RouteRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RouteRuleRepository extends JpaRepository<RouteRule, Long> {
    List<RouteRule> findByTenantIdAndStatusOrderByPriorityAsc(Long tenantId, RouteRule.Status status);
    List<RouteRule> findByTenantIdIsNullAndStatusOrderByPriorityAsc(RouteRule.Status status);
}
