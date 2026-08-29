package com.ycsopen.sms.core.repository;

import com.ycsopen.sms.core.domain.entity.BlacklistEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BlacklistEntryRepository extends JpaRepository<BlacklistEntry, Long> {
    /** tenantId 传 null 查系统级黑名单；F-5.1 要求系统级/机构级都要查一遍。 */
    List<BlacklistEntry> findByMobileHashAndTenantIdIsNullAndStatus(String mobileHash, BlacklistEntry.Status status);
    List<BlacklistEntry> findByMobileHashAndTenantIdAndStatus(String mobileHash, Long tenantId, BlacklistEntry.Status status);
}
