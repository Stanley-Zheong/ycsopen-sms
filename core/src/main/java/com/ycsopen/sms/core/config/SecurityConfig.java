package com.ycsopen.sms.core.config;

import com.ycsopen.sms.core.common.security.JwtAuthenticationFilter;
import com.ycsopen.sms.core.common.security.JwtAccessVerifier;
import com.ycsopen.sms.core.common.security.JwtTokenProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * PRD 6.2 节安全要求的最小落地：控制台走 JWT（无状态会话），HTTP 短信 API
 * 走独立的 HMAC 拦截器（见 web.interceptor.HmacAuthInterceptor），不复用 Spring Security 的会话体系。
 * <p>bcrypt 而非 MD5+盐——见 PRD 6.2.1 表格里明确写的那句话。</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtTokenProvider jwtTokenProvider,
                                           JwtAccessVerifier jwtAccessVerifier) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .logout(logout -> logout.disable())
            .exceptionHandling(exceptions -> exceptions
                    .authenticationEntryPoint((request, response, exception) ->
                            response.sendError(401, "Authentication required")))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(
                            "/api/v1/auth/**",
                            "/api/v1/console/auth/**",
                            "/api/v1/sms/**",
                            "/actuator/health").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/console/session/logout").authenticated()
                    .requestMatchers(HttpMethod.POST,
                            "/api/v1/console/tenants/registration-object-sessions",
                            "/api/v1/console/tenants/registration-object-sessions/{sessionId}/objects/{purpose}").permitAll()
                    .requestMatchers(HttpMethod.DELETE,
                            "/api/v1/console/tenants/registration-object-sessions/{sessionId}").permitAll()
            .requestMatchers("/api/v1/console/tenant/qualification")
                    .hasRole("TENANT_ADMIN")
                    .requestMatchers("/api/v1/console/tenant/administrators/**")
                    .hasRole("TENANT_ADMIN")
                    .requestMatchers("/api/v1/console/tenant/api-keys/**",
                            "/api/v1/console/tenant/cmpp-credentials/**",
                            "/api/v1/console/tenant/signatures/**",
                            "/api/v1/console/tenant/templates/**")
                    .hasAnyRole("TENANT_ADMIN", "TENANT_DEV")
                    .requestMatchers("/api/v1/console/tenant/shortlinks",
                            "/api/v1/console/tenant/shortlinks/**")
                    .hasAnyRole("TENANT_ADMIN", "TENANT_USER", "TENANT_DEV")
                    .requestMatchers("/api/v1/console/tenant/send")
                    .hasAnyRole("TENANT_ADMIN", "TENANT_USER", "TENANT_DEV")
                    .requestMatchers("/api/v1/console/**")
                    .hasAnyRole("ADMIN", "OPERATOR", "FINANCE")
                    .anyRequest().permitAll())
            .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, jwtAccessVerifier),
                    UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
