package com.ycsopen.sms.core.service.channel;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.web.dto.ChannelConfigurationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChannelConfigurationServiceTest {
    private JdbcTemplate jdbc;
    private ChannelConfigurationService service;

    @BeforeEach
    void setUp() {
        jdbc = ChannelConfigurationTestSupport.jdbc("channel-config");
        service = ChannelConfigurationTestSupport.service(jdbc);
    }

    @Test
    void createsSafeProjectionWithProtectedCredentialAndNormalizedPrice() {
        var response = service.create(7L, request("phase10-main", "CMPP", "channel-fixture.local", 7890,
                BigDecimal.valueOf(0.03123), null));

        assertThat(response.name()).isEqualTo("phase10-main");
        assertThat(response.price()).isEqualByComparingTo("0.0312");
        assertThat(response.priority()).isEqualTo(50);
        assertThat(response.tpsLimit()).isEqualTo(100);
        assertThat(response.availability()).isEqualTo("AVAILABLE");
        assertThat(response.account()).isEqualTo("******");
        assertThat(response.password()).isEqualTo("******");
        assertThat(response.extraConfig()).containsEntry("carrierProvince", "CN-FJ");

        byte[] account = jdbc.queryForObject("SELECT account_encrypted FROM channels WHERE id=?",
                byte[].class, response.id());
        byte[] password = jdbc.queryForObject("SELECT password_encrypted FROM channels WHERE id=?",
                byte[].class, response.id());
        assertThat(new String(account, StandardCharsets.UTF_8)).doesNotContain("account-a");
        assertThat(new String(password, StandardCharsets.UTF_8)).doesNotContain("Secret");
    }

    @Test
    void rejectsInvalidFieldBoundariesBeforePersistence() {
        assertThatThrownBy(() -> service.create(7L, request("x".repeat(51), "CMPP", "channel-fixture.local", 7890,
                BigDecimal.ONE, 50))).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo("CHANNEL_NAME_TOO_LONG"));
        assertThatThrownBy(() -> service.create(7L, request("bad-protocol", "SMTP", "channel-fixture.local", 7890,
                BigDecimal.ONE, 50))).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo("CHANNEL_PROTOCOL_INVALID"));
        assertThatThrownBy(() -> service.create(7L, request("bad-port", "CMPP", "channel-fixture.local", 0,
                BigDecimal.ONE, 50))).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo("CHANNEL_PORT_INVALID"));
        assertThatThrownBy(() -> service.create(7L, request("bad-price", "CMPP", "channel-fixture.local", 7890,
                BigDecimal.valueOf(-1), 50))).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo("CHANNEL_PRICE_INVALID"));
        assertThatThrownBy(() -> service.create(7L, request("bad-priority", "CMPP", "channel-fixture.local", 7890,
                BigDecimal.ONE, 101))).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo("CHANNEL_PRIORITY_INVALID"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM channels", Integer.class)).isZero();
    }

    @Test
    void duplicateNameIsRejectedWithoutPartialWrite() {
        service.create(7L, request("same-name", "CMPP", "channel-fixture.local", 7890, BigDecimal.ONE, 50));

        assertThatThrownBy(() -> service.create(7L, request("same-name", "SGIP", "channel-fixture.local", 8801,
                BigDecimal.ONE, 50))).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo("CHANNEL_NAME_DUPLICATED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM channels", Integer.class)).isEqualTo(1);
    }

    static ChannelConfigurationRequest request(String name, String protocol, String host, Integer port,
                                               BigDecimal price, Integer priority) {
        return new ChannelConfigurationRequest(name, protocol, "MOBILE", host, port,
                "account-a", "Secret-123", "SPID10", "svc10", "SRC10", 4, 8, 100,
                price, priority, "00:00-23:59", "AVAILABLE", Map.of("carrierProvince", "CN-FJ"), null);
    }
}
