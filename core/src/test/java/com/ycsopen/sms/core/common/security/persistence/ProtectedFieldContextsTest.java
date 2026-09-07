package com.ycsopen.sms.core.common.security.persistence;

import com.ycsopen.sms.core.common.security.envelope.ProtectionContext;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataManifest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ProtectedFieldContextsTest {
    @Test
    void liveUserPhoneUsesExactlyTheMigrationAuthenticatedData() throws Exception {
        var target = manifest().requireTarget("users.phone_encrypted");
        ProtectionContext migration = ProtectedFieldContexts.migration(target, "global", "42");

        assertThat(ProtectedFieldContexts.usersPhone(42L, null)).isEqualTo(migration);
    }

    @Test
    void tenantUserPhonePreservesTenantScope() throws Exception {
        var target = manifest().requireTarget("users.phone_encrypted");
        ProtectionContext migration = ProtectedFieldContexts.migration(target, "tenant:9", "42");

        assertThat(ProtectedFieldContexts.usersPhone(42L, 9L)).isEqualTo(migration);
    }

    private static ProtectedDataManifest manifest() throws Exception {
        byte[] bytes;
        try (var input = ProtectedFieldContextsTest.class.getResourceAsStream(
                "/security/protected-data-inventory.json")) {
            bytes = java.util.Objects.requireNonNull(input).readAllBytes();
        }
        return ProtectedDataManifest.load(new ByteArrayInputStream(bytes),
                ProtectedDataManifest.canonicalDigest(bytes));
    }
}
