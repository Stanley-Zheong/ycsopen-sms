package com.ycsopen.sms.core.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class TrustedProxyClientIpResolverTest {

    private final TrustedProxyClientIpResolver resolver = new TrustedProxyClientIpResolver();

    @Test
    void acceptsForwardedAddressOnlyFromLoopbackProxy() {
        MockHttpServletRequest proxied = new MockHttpServletRequest();
        proxied.setRemoteAddr("127.0.0.1");
        proxied.addHeader("X-Forwarded-For", "198.51.100.8");
        assertThat(resolver.resolve(proxied)).isEqualTo("198.51.100.8");

        MockHttpServletRequest direct = new MockHttpServletRequest();
        direct.setRemoteAddr("10.0.0.8");
        direct.addHeader("X-Forwarded-For", "198.51.100.9");
        assertThat(resolver.resolve(direct)).isEqualTo("10.0.0.8");
    }

    @Test
    void rejectsNonNumericForwardedValues() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("::1");
        request.addHeader("X-Forwarded-For", "attacker.example");

        assertThat(resolver.resolve(request)).isEqualTo("::1");
    }

    @Test
    void rejectsForwardedChainsFromAppendStyleProxyConfiguration() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.66, 198.51.100.8");

        assertThat(resolver.resolve(request)).isEqualTo("127.0.0.1");
    }

    @Test
    void rejectsHostnameShapedHexAndOutOfRangeIpv4WithoutDnsLookup() {
        MockHttpServletRequest hostname = new MockHttpServletRequest();
        hostname.setRemoteAddr("127.0.0.1");
        hostname.addHeader("X-Forwarded-For", "de.ad.be.ef");
        assertThat(resolver.resolve(hostname)).isEqualTo("127.0.0.1");

        MockHttpServletRequest invalidIpv4 = new MockHttpServletRequest();
        invalidIpv4.setRemoteAddr("::1");
        invalidIpv4.addHeader("X-Forwarded-For", "999.1.1.1");
        assertThat(resolver.resolve(invalidIpv4)).isEqualTo("::1");

        MockHttpServletRequest leadingDot = new MockHttpServletRequest();
        leadingDot.setRemoteAddr("127.0.0.1");
        leadingDot.addHeader("X-Forwarded-For", ".ff:1");
        assertThat(resolver.resolve(leadingDot)).isEqualTo("127.0.0.1");
    }
}
