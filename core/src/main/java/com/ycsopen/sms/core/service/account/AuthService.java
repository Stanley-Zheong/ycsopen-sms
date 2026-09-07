package com.ycsopen.sms.core.service.account;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.common.security.JwtTokenProvider;
import com.ycsopen.sms.core.domain.entity.User;
import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.web.dto.LoginRequest;
import com.ycsopen.sms.core.web.dto.LoginResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.time.LocalDate;

/**
 * F-1.4 登录与会话安全：bcrypt 校验密码（PRD 6.2.1 明确要求，非 MD5+盐），
 * 连续失败次数达到阈值后锁定账号。
 */
@Service
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final IdentitySessionService sessions;
    private final LoginAnomalyService anomalies;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider, IdentitySessionService sessions,
                       LoginAnomalyService anomalies) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.sessions = sessions;
        this.anomalies = anomalies;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResponse login(LoginRequest request, String clientIp) {
        return login(request, clientIp, null);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResponse login(LoginRequest request, String clientIp, String userAgent) {
        if (!PlatformAccountPolicy.fitsBcrypt(request.password())) {
            sessions.record(null, request.username(), clientIp, "INVALID_CREDENTIALS", userAgent);
            throw new BusinessException("INVALID_CREDENTIALS", "用户名或密码错误");
        }
        User user = userRepository.findByUsernameForUpdate(request.username()).orElse(null);
        if (user == null) {
            sessions.record(null, request.username(), clientIp, "INVALID_CREDENTIALS", userAgent);
            throw new BusinessException("INVALID_CREDENTIALS", "用户名或密码错误");
        }

        if (user.getStatus() == User.UserStatus.LOCKED) {
            sessions.record(user.getId(), user.getUsername(), clientIp, "ACCOUNT_LOCKED", userAgent);
            throw new BusinessException("ACCOUNT_LOCKED", "账号已被锁定，请联系管理员解锁");
        }
        if (user.getStatus() == User.UserStatus.DISABLED) {
            sessions.record(user.getId(), user.getUsername(), clientIp, "ACCOUNT_DISABLED", userAgent);
            throw new BusinessException("ACCOUNT_DISABLED", "账号已被禁用");
        }
        if (user.isPasswordExpired(LocalDateTime.now())) {
            sessions.record(user.getId(), user.getUsername(), clientIp, "PASSWORD_EXPIRED", userAgent);
            throw new BusinessException("PASSWORD_EXPIRED", "密码已过期，请联系管理员更新");
        }
        if (user.isAccountExpired(LocalDate.now())) {
            sessions.record(user.getId(), user.getUsername(), clientIp, "ACCOUNT_EXPIRED", userAgent);
            throw new BusinessException("ACCOUNT_EXPIRED", "账号有效期已结束，请联系管理员");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            int failures = user.getFailedLoginCount() == null ? 1 : user.getFailedLoginCount() + 1;
            user.setFailedLoginCount(failures);
            if (user.getFailedLoginCount() >= MAX_FAILED_ATTEMPTS) {
                user.setStatus(User.UserStatus.LOCKED);
            }
            userRepository.save(user);
            long historyId = sessions.recordWithId(
                    user.getId(), user.getUsername(), clientIp, "INVALID_CREDENTIALS", userAgent);
            if (failures == MAX_FAILED_ATTEMPTS) {
                anomalies.repeatedFailure(user.getId(), user.getTenantId(), historyId,
                        clientIp, MDC.get("traceId"));
            }
            throw new BusinessException("INVALID_CREDENTIALS", "用户名或密码错误");
        }

        boolean unusual = anomalies.isUnusual(user.getLastLoginIp(), clientIp);
        user.setFailedLoginCount(0);
        user.setLastLoginTime(LocalDateTime.now());
        user.setLastLoginIp(clientIp);
        userRepository.save(user);

        JwtTokenProvider.IssuedToken token = jwtTokenProvider.issueToken(
                user.getId(), user.getUserType().name(), user.getTenantId());
        sessions.open(user, token, clientIp, userAgent, unusual);
        if (unusual) {
            anomalies.enqueue(user.getId(), user.getTenantId(), token.sessionId(),
                    clientIp, MDC.get("traceId"));
        }
        return new LoginResponse(token.token(), user.getUserType().name(), user.getTenantId());
    }
}
