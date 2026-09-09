package com.ycsopen.sms.core.web.interceptor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.service.audit.OperationAuditService;
import com.ycsopen.sms.core.common.web.TrustedProxyClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Captures authenticated console HTTP operations without reading request values or bodies. */
@Component
public class OperationAuditInterceptor implements HandlerInterceptor {

    private static final String START_NANOS = OperationAuditInterceptor.class.getName() + ".start";
    private static final String AUDIT_ID = OperationAuditInterceptor.class.getName() + ".auditId";
    private static final Set<String> SAFE_PARAMETER_NAMES = Set.of(
            "page", "size", "actor", "operation", "result", "from", "to", "eventType",
            "userId", "all");
    private final OperationAuditService audits;
    private final ObjectMapper json;
    private final TrustedProxyClientIpResolver clientIps;

    public OperationAuditInterceptor(OperationAuditService audits, ObjectMapper json,
                                     TrustedProxyClientIpResolver clientIps) {
        this.audits = audits;
        this.json = json;
        this.clientIps = clientIps;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_NANOS, System.nanoTime());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authenticatedUser(authentication)) {
            long auditId = audits.start(command(request, authentication, "STARTED", 0, 0));
            request.setAttribute(AUDIT_ID, auditId);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception exception) {
        Object auditId = request.getAttribute(AUDIT_ID);
        if (!(auditId instanceof Long id)) {
            return;
        }
        int status = response.getStatus();
        audits.complete(id, result(status, exception), status, latency(request));
    }

    private OperationAuditService.AuditCommand command(HttpServletRequest request,
                                                       Authentication authentication,
                                                       String result, int status, long latency) {
        String route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) instanceof String value
                ? value : "/api/v1/console/unmatched";
        return new OperationAuditService.AuditCommand(
                Long.parseLong(authentication.getName()), request.getMethod() + " " + route,
                "CONSOLE_HTTP", resourceId(request), request.getMethod(), route,
                sanitizedRequest(request), result, status, clientIps.resolve(request),
                MDC.get("traceId"), latency);
    }

    private static boolean authenticatedUser(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && authentication.getName().matches("\\d+");
    }

    private String sanitizedRequest(HttpServletRequest request) {
        Map<String, String> fields = new TreeMap<>();
        request.getParameterMap().keySet().forEach(name -> {
            String safeName = SAFE_PARAMETER_NAMES.contains(name) ? name : "other";
            fields.put(safeName, "[redacted]");
        });
        try {
            return json.writeValueAsString(Map.of("fields", fields));
        } catch (JsonProcessingException impossible) {
            return "{\"fields\":[]}";
        }
    }

    @SuppressWarnings("unchecked")
    private static String resourceId(HttpServletRequest request) {
        Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(attribute instanceof Map<?, ?> variables)) return null;
        return variables.entrySet().stream()
                .filter(entry -> entry.getKey() instanceof String && entry.getValue() instanceof String)
                .map(entry -> Map.entry((String) entry.getKey(), (String) entry.getValue()))
                .filter(entry -> entry.getKey().matches("[A-Za-z][A-Za-z0-9]{0,31}")
                        && entry.getValue().matches("\\d{1,19}"))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "," + right)
                .orElse(null);
    }

    private static String result(int status, Exception exception) {
        if (exception != null || status >= 500) return "SERVER_FAILURE";
        if (status == 401 || status == 403) return "DENIED";
        if (status >= 400) return "CLIENT_FAILURE";
        return "SUCCESS";
    }

    private static long latency(HttpServletRequest request) {
        Object start = request.getAttribute(START_NANOS);
        return start instanceof Long value ? Math.max(0, (System.nanoTime() - value) / 1_000_000) : 0;
    }
}
