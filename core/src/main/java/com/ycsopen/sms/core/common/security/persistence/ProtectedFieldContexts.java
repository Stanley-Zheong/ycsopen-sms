package com.ycsopen.sms.core.common.security.persistence;

import com.ycsopen.sms.core.common.security.envelope.ProtectionContext;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataTarget;

/** Canonical authenticated-data bindings shared by migration and live protected-field access. */
public final class ProtectedFieldContexts {
    private static final String OWNER = "crypto-storage-bootstrap";

    private ProtectedFieldContexts() { }

    public static ProtectionContext migration(ProtectedDataTarget target,
                                              String tenantScope,
                                              String resourceIdentity) {
        return databaseField(target.table(), target.column(), tenantScope,
                target.identityColumn() + "=" + resourceIdentity);
    }

    public static ProtectionContext usersPhone(long userId, Long tenantId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("user identifier must be positive");
        }
        String tenantScope = tenantId == null ? "global" : "tenant:" + tenantId;
        return databaseField("users", "phone_encrypted", tenantScope, "id=" + userId);
    }

    private static ProtectionContext databaseField(String table, String column,
                                                    String tenantScope, String resourceIdentity) {
        return new ProtectionContext(
                ProtectionContext.Purpose.DATABASE_FIELD,
                OWNER,
                table,
                column,
                tenantScope,
                resourceIdentity);
    }
}
