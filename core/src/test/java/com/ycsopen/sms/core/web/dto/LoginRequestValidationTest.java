package com.ycsopen.sms.core.web.dto;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRequestValidationTest {

    @Test
    void rejectsUsernameLongerThanTheAccountContractBeforePersistence() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(
                    new LoginRequest("a".repeat(21), "Secure123"));

            assertThat(violations)
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactly("username");
        }
    }
}
