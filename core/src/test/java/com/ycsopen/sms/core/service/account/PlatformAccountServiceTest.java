package com.ycsopen.sms.core.service.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.User;
import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.web.dto.PlatformAccountCreateRequest;
import com.ycsopen.sms.core.web.dto.PlatformAccountUpdateRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class PlatformAccountServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-07T08:00:00Z"), ZoneOffset.UTC);

    @Mock UserRepository users;
    @Mock PlatformAccountPhoneStore phones;
    @Mock RoleAdministrationService roles;
    @Mock JdbcTemplate jdbc;

    @Test
    void createHashesPasswordProtectsPhoneAndAttributesActor() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
        when(users.findByUsername("operator_01")).thenReturn(Optional.empty());
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(21L);
            return saved;
        });
        when(phones.masked(21L)).thenReturn("138****8000");
        when(roles.currentRoleIds(21L)).thenReturn(List.of(3L));

        var response = service(encoder).create(new PlatformAccountCreateRequest(
                "operator_01", "Secure123", "13800138000", "ops@example.test",
                "Operator", "OPERATOR", LocalDate.of(2026, 12, 31), List.of(3L)), 7L);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("Secure123");
        assertThat(encoder.matches("Secure123", saved.getValue().getPasswordHash())).isTrue();
        assertThat(saved.getValue().getCreatedBy()).isEqualTo("7");
        assertThat(response.maskedPhone()).isEqualTo("138****8000");
        verify(phones).store(21L, "13800138000");
        verify(roles).assertPlatformAccountTypeGrantAllowed("OPERATOR", 7L);
        verify(roles).replaceUserRoles(21L, List.of(3L), 7L);
        verify(jdbc).update(any(String.class), any(), any(), any());
    }

    @Test
    void editRehashesSuppliedPassword() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
        User user = platformUser(21L, User.UserType.OPERATOR);
        user.setPasswordHash(encoder.encode("Original123"));
        user.setPasswordExpireTime(java.time.LocalDateTime.of(2026, 9, 1, 0, 0));
        when(users.findById(21L)).thenReturn(Optional.of(user));
        when(users.findByUsername("finance_01")).thenReturn(Optional.empty());
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(phones.masked(21L)).thenReturn("139****9000");
        when(roles.currentRoleIds(21L)).thenReturn(List.of(4L));

        service(encoder).update(21L, new PlatformAccountUpdateRequest(
                "finance_01", "Changed123", "13900139000", "finance@example.test",
                "Finance", "FINANCE", null, List.of(4L)), 7L);

        assertThat(encoder.matches("Changed123", user.getPasswordHash())).isTrue();
        assertThat(user.getPasswordExpireTime()).isNull();
        assertThat(user.getUserType()).isEqualTo(User.UserType.FINANCE);
        verify(users).saveAndFlush(user);
        verify(phones).store(21L, "13900139000");
        verify(roles).replaceUserRoles(21L, List.of(4L), 7L);
        verify(jdbc).update("""
                UPDATE user_sessions SET revoked_at = COALESCE(revoked_at, CURRENT_TIMESTAMP)
                WHERE user_id = ? AND revoked_at IS NULL
                """, 21L);
    }

    @Test
    void listReturnsOnlyPlatformAccountsWithMaskedPhoneAndNoCredentialField() throws Exception {
        User admin = platformUser(1L, User.UserType.ADMIN);
        admin.setPasswordHash("secret-hash");
        User tenant = platformUser(2L, User.UserType.TENANT_ADMIN);
        when(users.findAll()).thenReturn(List.of(admin, tenant));
        when(phones.masked(1L)).thenReturn("138****8000");
        when(roles.currentRoleIds(1L)).thenReturn(List.of(2L));

        var responses = service(new BCryptPasswordEncoder(4)).list();

        assertThat(responses).singleElement().satisfies(response -> {
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.maskedPhone()).isEqualTo("138****8000");
        });
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(responses);
        assertThat(json).doesNotContain("secret-hash", "13800138000", "password");
    }

    @Test
    void editWithBlankPhonePreservesProtectedPhone() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
        User user = platformUser(21L, User.UserType.OPERATOR);
        user.setPasswordHash(encoder.encode("Original123"));
        when(users.findById(21L)).thenReturn(Optional.of(user));
        when(users.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(phones.masked(21L)).thenReturn("139****9000");
        when(roles.currentRoleIds(21L)).thenReturn(List.of(4L));

        service(encoder).update(21L, new PlatformAccountUpdateRequest(
                user.getUsername(), null, "", null, "Operator",
                "OPERATOR", null, List.of(4L)), 7L);

        verify(phones, never()).store(anyLong(), any(String.class));
    }

    @Test
    void invalidCreateInputIsReportedAsAClientBusinessRejection() {
        assertThatThrownBy(() -> service(new BCryptPasswordEncoder(4)).create(
                new PlatformAccountCreateRequest("x", "weak", "123", null,
                        null, "TENANT_ADMIN", LocalDate.of(2026, 9, 6), List.of(1L)), 7L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo("INVALID_PLATFORM_ACCOUNT");
    }

    @Test
    void accountCannotChangeItsOwnIdentityOrRoleAssignment() {
        assertThatThrownBy(() -> service(new BCryptPasswordEncoder(4)).update(
                21L,
                new PlatformAccountUpdateRequest(
                        "operator_01", null, "", null, "Operator",
                        "OPERATOR", null, List.of(4L)),
                21L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo("SELF_ACCOUNT_EDIT_FORBIDDEN");

        verify(users, never()).findById(anyLong());
        verify(roles, never()).replaceUserRoles(anyLong(), any(), anyLong());
    }

    @Test
    void delegatedManagerCannotCreateAdministratorAccount() {
        doThrow(new BusinessException("ADMIN_GRANT_FORBIDDEN", "只有管理员可以创建管理员账号"))
                .when(roles).assertPlatformAccountTypeGrantAllowed("ADMIN", 7L);

        assertThatThrownBy(() -> service(new BCryptPasswordEncoder(4)).create(
                new PlatformAccountCreateRequest(
                        "admin_02", "Secure123", "13800138000", null,
                        "Admin", "ADMIN", null, List.of(1L)),
                7L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo("ADMIN_GRANT_FORBIDDEN");

        verify(users, never()).saveAndFlush(any(User.class));
        verify(roles, never()).replaceUserRoles(anyLong(), any(), anyLong());
    }

    private PlatformAccountService service(BCryptPasswordEncoder encoder) {
        return new PlatformAccountService(users, encoder, phones, roles, jdbc, CLOCK);
    }

    private static User platformUser(long id, User.UserType type) {
        User user = new User();
        user.setId(id);
        user.setUsername("account_" + id);
        user.setUserType(type);
        user.setStatus(User.UserStatus.ACTIVE);
        return user;
    }
}
