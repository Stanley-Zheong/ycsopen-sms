package com.ycsopen.sms.core.service.account;

import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.web.dto.PlatformAccountCreateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({PlatformAccountService.class, PlatformAccountServiceTransactionTest.Dependencies.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PlatformAccountServiceTransactionTest {

    @Autowired PlatformAccountService accounts;
    @Autowired PlatformAccountPhoneStore phones;
    @Autowired RoleAdministrationService roles;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        reset(phones, roles);
        users.deleteAll();
    }

    @Test
    void roleFailureRollsBackThePersistedAccountAndProtectedPhoneWrite() {
        doAnswer(invocation -> {
            jdbc.update("UPDATE users SET phone_encrypted = ? WHERE id = ?",
                    new byte[]{1, 2, 3}, invocation.getArgument(0, Long.class));
            return null;
        }).when(phones).store(anyLong(), eq("13800138000"));
        doThrow(new IllegalStateException("forced role persistence failure"))
                .when(roles).replaceUserRoles(anyLong(), eq(List.of(3L)), eq(7L));

        assertThatThrownBy(() -> accounts.create(new PlatformAccountCreateRequest(
                "operator_01", "Secure123", "13800138000", "ops@example.test",
                "Operator", "OPERATOR", LocalDate.now().plusDays(1), List.of(3L)), 7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("forced role persistence failure");

        verify(phones).store(anyLong(), eq("13800138000"));
        assertThat(users.count()).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE phone_encrypted IS NOT NULL", Integer.class))
                .isZero();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Dependencies {
        @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(4); }
        @Bean PlatformAccountPhoneStore platformAccountPhoneStore() {
            return mock(PlatformAccountPhoneStore.class);
        }
        @Bean RoleAdministrationService roleAdministrationService() {
            return mock(RoleAdministrationService.class);
        }
    }
}
