package com.ycsopen.sms.core.service.account;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformAccountPolicyTest {
    private final LocalDate today = LocalDate.of(2026, 9, 6);

    @Test
    void acceptsValidPlatformAccount() {
        assertThatNoException().isThrownBy(() -> PlatformAccountPolicy.validate(
                "admin_01", "Secure123", "13800138000", "ADMIN", null, today));
    }

    @Test
    void rejectsInvalidFields() {
        assertThatThrownBy(() -> PlatformAccountPolicy.validate(
                "a", "weak", "123", "TENANT_ADMIN", today.minusDays(1), today))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPasswordsBeyondBcryptUtf8Boundary() {
        String overlong = "Secure123" + "密".repeat(22);
        assertThatThrownBy(() -> PlatformAccountPolicy.validatePassword(overlong))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankRealName() {
        assertThatThrownBy(() -> PlatformAccountPolicy.validateRealName("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsValuesWiderThanTheUsersTableColumns() {
        assertThatThrownBy(() -> PlatformAccountPolicy.validateRealName("名".repeat(51)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlatformAccountPolicy.validateEmail("a".repeat(101)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
