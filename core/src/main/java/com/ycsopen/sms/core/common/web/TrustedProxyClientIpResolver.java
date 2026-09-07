package com.ycsopen.sms.core.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/** Resolves client IPs while trusting forwarding headers only from a local reverse proxy. */
@Component
public class TrustedProxyClientIpResolver {

    public String resolve(HttpServletRequest request) {
        String remote = bounded(request.getRemoteAddr());
        if (!isLoopback(remote)) {
            return remote;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return remote;
        }
        // The supported deployment has exactly one same-host proxy which overwrites XFF.
        // Reject a chain instead of trusting an attacker-controlled address prepended by a
        // misconfigured proxy_add_x_forwarded_for directive.
        String single = forwarded.trim();
        if (single.contains(",")) {
            return remote;
        }
        return isNumericAddress(single) ? single : remote;
    }

    private static boolean isLoopback(String value) {
        return "127.0.0.1".equals(value) || "0:0:0:0:0:0:0:1".equals(value) || "::1".equals(value);
    }

    private static boolean isNumericAddress(String value) {
        if (value.isBlank() || value.length() > 45) {
            return false;
        }
        if (!value.contains(":")) {
            String[] octets = value.split("\\.", -1);
            if (octets.length != 4) return false;
            for (String octet : octets) {
                if (!octet.matches("[0-9]{1,3}") || Integer.parseInt(octet) > 255) return false;
            }
            return true;
        }
        char first = value.charAt(0);
        if (!(first == ':' || Character.digit(first, 16) >= 0)
                || !value.matches("[0-9A-Fa-f:.]+")) return false;
        try {
            return InetAddress.getByName(value) instanceof Inet6Address;
        } catch (UnknownHostException invalid) {
            return false;
        }
    }

    private static String bounded(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.length() <= 45 ? value : "unknown";
    }
}
