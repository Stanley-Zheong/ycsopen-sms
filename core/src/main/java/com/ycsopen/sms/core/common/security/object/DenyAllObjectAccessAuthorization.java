package com.ycsopen.sms.core.common.security.object;

/** Production-safe policy until Phase 6 supplies current RBAC and reveal policy. */
public final class DenyAllObjectAccessAuthorization implements ObjectAccessAuthorizationPort {

    @Override
    public boolean authorize(Request request) {
        return false;
    }
}
