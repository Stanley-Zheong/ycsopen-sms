package com.ycsopen.sms.core.config;

import com.ycsopen.sms.core.web.interceptor.HmacAuthInterceptor;
import com.ycsopen.sms.core.web.interceptor.OperationAuditInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final HmacAuthInterceptor hmacAuthInterceptor;
    private final OperationAuditInterceptor operationAuditInterceptor;

    public WebMvcConfig(HmacAuthInterceptor hmacAuthInterceptor,
                        OperationAuditInterceptor operationAuditInterceptor) {
        this.hmacAuthInterceptor = hmacAuthInterceptor;
        this.operationAuditInterceptor = operationAuditInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(hmacAuthInterceptor).addPathPatterns("/api/v1/sms/**");
        registry.addInterceptor(operationAuditInterceptor)
                .addPathPatterns("/api/v1/console/**")
                .excludePathPatterns("/api/v1/console/auth/**");
    }
}
