package com.ycsopen.sms.core.common.security;

import com.ycsopen.sms.core.domain.entity.User;
import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.service.account.RoleAdministrationService;
import com.ycsopen.sms.core.service.account.IdentitySessionService;
import io.jsonwebtoken.Claims;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Rechecks mutable identity and permission state for every authenticated request. */
@Service
public class JwtAccessVerifier {
    private final UserRepository users;
    private final RoleAdministrationService roles;
    private final IdentitySessionService sessions;

    public JwtAccessVerifier(UserRepository users, RoleAdministrationService roles,
                             IdentitySessionService sessions) {
        this.users = users;
        this.roles = roles;
        this.sessions = sessions;
    }

    public VerifiedAccess verify(Claims claims) {
        long userId;
        try {
            userId = Long.parseLong(claims.getSubject());
        } catch (RuntimeException failure) {
            throw new BadCredentialsException("invalid token subject", failure);
        }
        User user = users.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("account no longer exists"));
        if (user.getStatus() != User.UserStatus.ACTIVE
                || user.isPasswordExpired(LocalDateTime.now())
                || user.isAccountExpired(LocalDate.now())) {
            throw new BadCredentialsException("account is not active");
        }
        String claimedType = claims.get("userType", String.class);
        if (claimedType == null || !user.getUserType().name().equals(claimedType)) {
            throw new BadCredentialsException("token role is stale");
        }
        if (claims.getId() == null || !sessions.isActive(claims.getId(), userId)) {
            throw new BadCredentialsException("session is expired or revoked");
        }
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + claimedType));
        roles.currentPermissions(userId).stream()
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);
        return new VerifiedAccess(String.valueOf(userId), authorities);
    }

    public record VerifiedAccess(String subject, List<SimpleGrantedAuthority> authorities) { }
}
