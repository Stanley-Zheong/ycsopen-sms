package com.ycsopen.sms.core.notification.provider;

/** Controlled templates used before tenant channel routing is available. */
public enum PlatformNotificationTemplate {
    REGISTRATION("platform.registration"),
    OPERATIONAL("platform.operational");

    private final String code;

    PlatformNotificationTemplate(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
