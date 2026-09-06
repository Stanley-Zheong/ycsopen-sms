package com.ycsopen.sms.core.common.security.persistence;

import com.ycsopen.sms.core.domain.entity.BlacklistEntry;
import com.ycsopen.sms.core.repository.BlacklistEntryRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Package-scoped sole owner of pre-COMPLETE raw-SHA compatibility reads. */
final class LegacyMobileHashReader {

    private final BlacklistEntryRepository blacklistEntryRepository;

    LegacyMobileHashReader(BlacklistEntryRepository blacklistEntryRepository) {
        this.blacklistEntryRepository = Objects.requireNonNull(
                blacklistEntryRepository, "blacklistEntryRepository");
    }

    List<BlindIndexLookupService.BlacklistMatch> readBlacklist(
            LegacyMobileLookupToken token,
            long tenantId,
            BlacklistEntry.Status status) {
        Objects.requireNonNull(token, "token");
        byte[] digest = token.copyDigestForLegacyRead();
        try {
            String legacyIndex = HexFormat.of().formatHex(digest);
            List<BlindIndexLookupService.BlacklistMatch> matches = new ArrayList<>();
            blacklistEntryRepository.findSystemLegacyCompatibilityMatches(legacyIndex, status)
                    .forEach(row -> matches.add(toMatch(row)));
            blacklistEntryRepository.findTenantLegacyCompatibilityMatches(legacyIndex, tenantId, status)
                    .forEach(row -> matches.add(toMatch(row)));
            return List.copyOf(matches);
        } finally {
            Arrays.fill(digest, (byte) 0);
        }
    }

    private static BlindIndexLookupService.BlacklistMatch toMatch(
            BlacklistEntryRepository.LookupProjection row) {
        if (row == null || row.getId() == null || row.getStatus() == null
                || row.getListType() == null) {
            throw new IllegalStateException(BlindIndexLookupService.SANITIZED_FAILURE);
        }
        return new BlindIndexLookupService.BlacklistMatch(
                row.getId(), row.getTenantId(), row.getListType(), row.getStatus(), null);
    }
}
