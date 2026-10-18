package com.ycsopen.sms.core.common.security;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;

/** Shared parser and matcher for the IP literals accepted by credential creation and authentication. */
public final class IpAllowList {
    private IpAllowList() { }

    public static boolean isValid(String value) {
        if (value == null || value.isBlank()) return true;
        try {
            return !rules(value).isEmpty();
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    public static String canonicalJson(String value) {
        if (value == null || value.isBlank()) return null;
        List<String> entries = entries(value);
        entries.forEach(IpAllowList::parseRule);
        return "[\"" + String.join("\",\"", entries) + "\"]";
    }

    public static boolean contains(String value, String remoteAddress) {
        if (value == null || value.isBlank()) return true;
        try {
            byte[] candidate = address(remoteAddress);
            return rules(value).stream().anyMatch(rule -> rule.contains(candidate));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static List<Rule> rules(String value) {
        return entries(value).stream().map(IpAllowList::parseRule).toList();
    }

    private static List<String> entries(String value) {
        String normalized = value.trim();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        if (normalized.isBlank()) throw new IllegalArgumentException("empty allow list");
        return Arrays.stream(normalized.split(",", -1)).map(String::trim).map(entry -> {
            if (entry.length() >= 2 && entry.startsWith("\"") && entry.endsWith("\"")) {
                return entry.substring(1, entry.length() - 1);
            }
            return entry;
        }).peek(entry -> {
            if (entry.isBlank()) throw new IllegalArgumentException("empty allow-list rule");
        }).toList();
    }

    private static Rule parseRule(String value) {
        String[] parts = value.split("/", -1);
        if (parts.length > 2 || parts[0].isBlank()) throw new IllegalArgumentException("invalid rule");
        byte[] network = address(parts[0]);
        int maxPrefix = network.length * Byte.SIZE;
        int prefix = maxPrefix;
        if (parts.length == 2) {
            try {
                prefix = Integer.parseInt(parts[1]);
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("invalid prefix", invalid);
            }
            if (prefix < 0 || prefix > maxPrefix) throw new IllegalArgumentException("invalid prefix");
        }
        return new Rule(network, prefix);
    }

    private static byte[] address(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("missing address");
        if (value.contains(":")) {
            if (!value.matches("[0-9A-Fa-f:]+")) throw new IllegalArgumentException("invalid ipv6");
            try {
                byte[] parsed = InetAddress.getByName(value).getAddress();
                if (parsed.length != 16) throw new IllegalArgumentException("invalid ipv6");
                return parsed;
            } catch (Exception invalid) {
                throw new IllegalArgumentException("invalid ipv6", invalid);
            }
        }
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) throw new IllegalArgumentException("invalid ipv4");
        byte[] parsed = new byte[4];
        for (int index = 0; index < octets.length; index++) {
            if (!octets[index].matches("[0-9]{1,3}")) throw new IllegalArgumentException("invalid ipv4");
            int octet = Integer.parseInt(octets[index]);
            if (octet > 255) throw new IllegalArgumentException("invalid ipv4");
            parsed[index] = (byte) octet;
        }
        return parsed;
    }

    private record Rule(byte[] network, int prefix) {
        private boolean contains(byte[] candidate) {
            if (network.length != candidate.length) return false;
            int wholeBytes = prefix / Byte.SIZE;
            int remainingBits = prefix % Byte.SIZE;
            for (int index = 0; index < wholeBytes; index++) {
                if (network[index] != candidate[index]) return false;
            }
            if (remainingBits == 0) return true;
            int mask = 0xff << (Byte.SIZE - remainingBits);
            return (network[wholeBytes] & mask) == (candidate[wholeBytes] & mask);
        }
    }
}
