package com.ycsopen.sms.core.service.account;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.User;
import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.web.dto.PlatformAccountCreateRequest;
import com.ycsopen.sms.core.web.dto.PlatformAccountResponse;
import com.ycsopen.sms.core.web.dto.PlatformAccountUpdateRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

/** Administrator-facing platform-account application service. */
@Service
public class PlatformAccountService {

    private static final EnumSet<User.UserType> PLATFORM_TYPES = EnumSet.of(
            User.UserType.ADMIN, User.UserType.OPERATOR, User.UserType.FINANCE);

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final PlatformAccountPhoneStore phones;
    private final RoleAdministrationService roles;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public PlatformAccountService(UserRepository users,
                                  PasswordEncoder passwords,
                                  PlatformAccountPhoneStore phones,
                                  RoleAdministrationService roles,
                                  JdbcTemplate jdbc) {
        this(users, passwords, phones, roles, jdbc, Clock.systemDefaultZone());
    }

    PlatformAccountService(UserRepository users,
                           PasswordEncoder passwords,
                           PlatformAccountPhoneStore phones,
                           RoleAdministrationService roles,
                           JdbcTemplate jdbc,
                           Clock clock) {
        this.users = users;
        this.passwords = passwords;
        this.phones = phones;
        this.roles = roles;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PlatformAccountResponse> list() {
        return users.findAll().stream()
                .filter(user -> PLATFORM_TYPES.contains(user.getUserType()))
                .sorted(java.util.Comparator.comparing(User::getId))
                .map(this::response)
                .toList();
    }

    @Transactional
    public PlatformAccountResponse create(PlatformAccountCreateRequest request, long actorUserId) {
        LocalDate today = LocalDate.now(clock);
        validateCreate(request, today);
        roles.assertPlatformAccountTypeGrantAllowed(request.userType(), actorUserId);
        requireAvailableUsername(request.username(), null);

        User user = new User();
        applyProfile(user, request.username(), request.email(), request.realName(),
                request.userType(), request.validUntil());
        user.setPasswordHash(passwords.encode(request.password()));
        user.setCreatedBy(String.valueOf(actorUserId));
        user.setStatus(User.UserStatus.ACTIVE);
        user.setFailedLoginCount(0);
        user = users.saveAndFlush(user);
        phones.store(user.getId(), request.phone());
        roles.replaceUserRoles(user.getId(), request.roleIds(), actorUserId);
        audit(user.getId(), actorUserId, "CREATE");
        return response(user);
    }

    @Transactional
    public PlatformAccountResponse update(long userId,
                                          PlatformAccountUpdateRequest request,
                                          long actorUserId) {
        if (userId == actorUserId) {
            throw new BusinessException("SELF_ACCOUNT_EDIT_FORBIDDEN", "不能修改自己的账号或角色");
        }
        LocalDate today = LocalDate.now(clock);
        validateUpdate(request, today);
        User user = requirePlatformUser(userId);
        if (user.getUserType() == User.UserType.ADMIN || "ADMIN".equals(request.userType())) {
            roles.assertPlatformAccountTypeGrantAllowed("ADMIN", actorUserId);
        }
        requireAvailableUsername(request.username(), userId);
        applyProfile(user, request.username(), request.email(), request.realName(),
                request.userType(), request.validUntil());
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwords.encode(request.password()));
            user.setPasswordExpireTime(null);
            jdbc.update("""
                    UPDATE user_sessions SET revoked_at = COALESCE(revoked_at, CURRENT_TIMESTAMP)
                    WHERE user_id = ? AND revoked_at IS NULL
                    """, userId);
        }
        // Flush the entity before the direct protected-column update so Hibernate cannot
        // overwrite the new envelope with the previously loaded phone value at commit.
        user = users.saveAndFlush(user);
        if (request.phone() != null && !request.phone().isBlank()) {
            phones.store(userId, request.phone());
        }
        roles.replaceUserRoles(userId, request.roleIds(), actorUserId);
        audit(userId, actorUserId, "UPDATE");
        return response(user);
    }

    private void applyProfile(User user,
                              String username,
                              String email,
                              String realName,
                              String userType,
                              LocalDate validUntil) {
        user.setUsername(username);
        user.setEmail(emptyToNull(email));
        user.setRealName(emptyToNull(realName));
        user.setUserType(User.UserType.valueOf(userType));
        user.setTenantId(null);
        user.setValidUntil(validUntil);
    }

    private PlatformAccountResponse response(User user) {
        return new PlatformAccountResponse(
                user.getId(), user.getUsername(), user.getEmail(), user.getRealName(),
                phones.masked(user.getId()), user.getUserType().name(), user.getStatus().name(),
                user.getValidUntil(), roles.currentRoleIds(user.getId()), user.getLastLoginTime(),
                user.getCreatedBy(), user.getCreatedAt());
    }

    private User requirePlatformUser(long userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "账号不存在"));
        if (!PLATFORM_TYPES.contains(user.getUserType())) {
            throw new BusinessException("PLATFORM_ACCOUNT_REQUIRED", "只能管理平台账号");
        }
        return user;
    }

    private void requireAvailableUsername(String username, Long currentUserId) {
        users.findByUsername(username).ifPresent(existing -> {
            if (currentUserId == null || !currentUserId.equals(existing.getId())) {
                throw new BusinessException("USERNAME_EXISTS", "用户名已存在");
            }
        });
    }

    private void audit(long userId, long actorUserId, String action) {
        jdbc.update("""
                INSERT INTO account_change_history(user_id, actor_user_id, action, occurred_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """, userId, actorUserId, action);
    }

    private static void validateCreate(PlatformAccountCreateRequest request, LocalDate today) {
        try {
            PlatformAccountPolicy.validate(request.username(), request.password(), request.phone(),
                    request.userType(), request.validUntil(), today);
            PlatformAccountPolicy.validateRealName(request.realName());
            PlatformAccountPolicy.validateEmail(request.email());
        } catch (IllegalArgumentException failure) {
            throw invalidAccount();
        }
    }

    private static void validateUpdate(PlatformAccountUpdateRequest request, LocalDate today) {
        try {
            PlatformAccountPolicy.validateEditableProfile(
                    request.username(), request.userType(), request.validUntil(), today);
            PlatformAccountPolicy.validateRealName(request.realName());
            PlatformAccountPolicy.validateEmail(request.email());
            if (request.phone() != null && !request.phone().isBlank()) {
                PlatformAccountPolicy.validatePhone(request.phone());
            }
            if (request.password() != null && !request.password().isBlank()) {
                PlatformAccountPolicy.validatePassword(request.password());
            }
        } catch (IllegalArgumentException failure) {
            throw invalidAccount();
        }
    }

    private static BusinessException invalidAccount() {
        return new BusinessException("INVALID_PLATFORM_ACCOUNT", "平台账号字段不合法");
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
