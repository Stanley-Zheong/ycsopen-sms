package com.ycsopen.sms.core.cmpp;

import java.util.Optional;

/** Lookup boundary for tenant-facing CMPP credentials. */
public interface CmppDownstreamCredentialStore {
    Optional<Credential> findActiveByAccount(String account);

    record Credential(long credentialId, long tenantId, String account, String password, String spid,
                      String ipWhitelist, int maxConnections, int tpsLimit, int windowSize) {
        public Credential {
            if (credentialId <= 0 || tenantId <= 0 || blank(account) || blank(password)
                    || maxConnections <= 0 || tpsLimit <= 0 || windowSize <= 0) {
                throw new IllegalArgumentException("invalid downstream CMPP credential");
            }
            account = account.trim();
            password = password.trim();
            spid = blank(spid) ? account : spid.trim();
            ipWhitelist = blank(ipWhitelist) ? "*" : ipWhitelist.trim();
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
