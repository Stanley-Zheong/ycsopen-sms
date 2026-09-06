package com.ycsopen.sms.core.common.security.object;

import java.util.Arrays;

/**
 * One-time application-relative capability path. The sensitive path is never
 * included in string rendering and is cleared from this value after delivery.
 */
public final class ObjectCapabilityToken {

    private char[] applicationRelativePath;

    ObjectCapabilityToken(String applicationRelativePath) {
        if (applicationRelativePath == null || applicationRelativePath.isBlank()
                || !applicationRelativePath.startsWith(ObjectCapabilityService.CAPABILITY_PATH_PREFIX)) {
            throw new IllegalArgumentException("invalid capability path");
        }
        this.applicationRelativePath = applicationRelativePath.toCharArray();
    }

    /** Returns the complete path once and clears the retained character buffer. */
    public synchronized String claimApplicationRelativePath() {
        if (applicationRelativePath == null) {
            throw new IllegalStateException("capability path already claimed");
        }
        String claimed = new String(applicationRelativePath);
        Arrays.fill(applicationRelativePath, '\0');
        applicationRelativePath = null;
        return claimed;
    }

    @Override
    public String toString() {
        return "ObjectCapabilityToken[applicationRelativePath=[redacted]]";
    }
}
