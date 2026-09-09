package com.ycsopen.sms.core.service.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.charset.StandardCharsets;

final class ChannelConfigurationTestSupport {
    private ChannelConfigurationTestSupport() { }

    static JdbcTemplate jdbc(String name) {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + name + "-" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE channels (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    channel_name VARCHAR(50) NOT NULL UNIQUE,
                    protocol VARCHAR(16) NOT NULL,
                    operator VARCHAR(24) NOT NULL,
                    host VARCHAR(128),
                    port INT,
                    account_encrypted VARBINARY(255),
                    password_encrypted VARBINARY(255),
                    sp_id VARCHAR(32),
                    service_id VARCHAR(16),
                    src_id VARCHAR(32),
                    max_connections INT NOT NULL DEFAULT 10,
                    window_size INT NOT NULL DEFAULT 8,
                    tps_limit INT NOT NULL DEFAULT 100,
                    price DECIMAL(10,4) NOT NULL DEFAULT 0,
                    priority INT NOT NULL DEFAULT 50,
                    active_window VARCHAR(64),
                    availability VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
                    extra_config VARCHAR(1000),
                    effective_version_id BIGINT NULL,
                    configuration_version BIGINT NOT NULL DEFAULT 0,
                    status VARCHAR(24) NOT NULL DEFAULT 'NORMAL',
                    pause_reason VARCHAR(255),
                    paused_by VARCHAR(64),
                    paused_at TIMESTAMP,
                    resumed_at TIMESTAMP,
                    offline_by VARCHAR(64),
                    offline_at TIMESTAMP,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE channel_configuration_versions (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    channel_id BIGINT NOT NULL,
                    payload_json VARCHAR(4000) NOT NULL,
                    status VARCHAR(24) NOT NULL,
                    reason_code VARCHAR(64),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("CREATE TABLE route_rules (id BIGINT AUTO_INCREMENT PRIMARY KEY, target_channel_id BIGINT, status VARCHAR(24))");
        jdbc.execute("CREATE TABLE channel_group_members (group_id BIGINT, channel_id BIGINT, PRIMARY KEY(group_id, channel_id))");
        jdbc.execute("CREATE TABLE signature_channel_registrations (id BIGINT AUTO_INCREMENT PRIMARY KEY, channel_id BIGINT, reg_status VARCHAR(24))");
        jdbc.execute("CREATE TABLE message_tasks (id BIGINT AUTO_INCREMENT PRIMARY KEY, channel_id BIGINT, send_status VARCHAR(24))");
        return jdbc;
    }

    static ChannelConfigurationService service(JdbcTemplate jdbc) {
        return new ChannelConfigurationService(jdbc, protector(), new ChannelConnectivityAdapter(),
                new ObjectMapper(), null);
    }

    static ChannelSecretProtector protector() {
        return (channelId, field, value) -> {
            byte[] protectedValue = ("enc:" + channelId + ":" + field).getBytes(StandardCharsets.UTF_8);
            java.util.Arrays.fill(value, '\0');
            return protectedValue;
        };
    }
}
