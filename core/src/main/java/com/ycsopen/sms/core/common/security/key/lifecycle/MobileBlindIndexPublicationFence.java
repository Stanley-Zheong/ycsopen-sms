package com.ycsopen.sms.core.common.security.key.lifecycle;

import java.util.List;

/** Transaction-scoped publication fence shared by every MOBILE blind-index producer. */
public interface MobileBlindIndexPublicationFence {

    /** Returns false unless the locked writable key set exactly equals the canonical request. */
    boolean lockAndValidate(List<ExpectedKey> expected);

    record ExpectedKey(long keyVersion, KeyState state) {
        public ExpectedKey {
            if (keyVersion < 1 || state != KeyState.ACTIVE && state != KeyState.RETIRING) {
                throw new IllegalArgumentException("mobile blind-index key set is invalid");
            }
        }
    }
}
