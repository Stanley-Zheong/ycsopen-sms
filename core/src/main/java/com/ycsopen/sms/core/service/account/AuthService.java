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

import java.time.LocalDateTime;

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

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public LoginResponse login(LoginRequest request, String clientIp) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BusinessException("INVALID_CREDENTIALS", "用户名或密码错误"));

        if (user.getStatus() == User.UserStatus.LOCKED) {
            throw new BusinessException("ACCOUNT_LOCKED", "账号已被锁定，请联系管理员解锁");
        }
        if (user.getStatus() == User.UserStatus.DISABLED) {
            throw new BusinessException("ACCOUNT_DISABLED", "账号已被禁用");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.setFailedLoginCount(user.getFailedLoginCount() + 1);
            if (user.getFailedLoginCount() >= MAX_FAILED_ATTEMPTS) {
                user.setStatus(User.UserStatus.LOCKED);
            }
            userRepository.save(user);
            throw new BusinessException("INVALID_CREDENTIALS", "用户名或密码错误");
        }

        user.setFailedLoginCount(0);
        user.setLastLoginTime(LocalDateTime.now());
        user.setLastLoginIp(clientIp);
        userRepository.save(user);

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUserType().name(), user.getTenantId());
        return new LoginResponse(token, user.getUserType().name(), user.getTenantId());
    }
}
