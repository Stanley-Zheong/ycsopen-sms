package com.ycsopen.sms.core.repository;

import com.ycsopen.sms.core.domain.entity.BlacklistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BlacklistEntryRepository extends JpaRepository<BlacklistEntry, Long> {
    /**
     * Legacy compatibility lookup only. Plan 03-10 owns the ACTIVE/RETIRING blind-index union and
     * checkpoint-gated legacy fallback. This projection must never hydrate {@code mobile_encrypted}.
     */
    @Query("""
            select entry.id as id,
                   entry.status as status,
                   entry.listType as listType,
                   entry.tenantId as tenantId,
                   entry.mobileHash as legacyIndex
              from BlacklistEntry entry
             where entry.mobileHash = :legacyIndex
               and entry.tenantId is null
               and entry.status = :status
            """)
    List<LookupProjection> findSystemLegacyCompatibilityMatches(
            @Param("legacyIndex") String legacyIndex,
            @Param("status") BlacklistEntry.Status status);

    /** @see #findSystemLegacyCompatibilityMatches(String, BlacklistEntry.Status) */
    @Query("""
            select entry.id as id,
                   entry.status as status,
                   entry.listType as listType,
                   entry.tenantId as tenantId,
                   entry.mobileHash as legacyIndex
              from BlacklistEntry entry
             where entry.mobileHash = :legacyIndex
               and entry.tenantId = :tenantId
               and entry.status = :status
            """)
    List<LookupProjection> findTenantLegacyCompatibilityMatches(
            @Param("legacyIndex") String legacyIndex,
            @Param("tenantId") Long tenantId,
            @Param("status") BlacklistEntry.Status status);

    interface LookupProjection {
        Long getId();

        BlacklistEntry.Status getStatus();

        BlacklistEntry.ListType getListType();

        Long getTenantId();

        String getLegacyIndex();
    }
}
