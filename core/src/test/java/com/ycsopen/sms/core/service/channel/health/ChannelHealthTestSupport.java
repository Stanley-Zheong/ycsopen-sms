package com.ycsopen.sms.core.service.channel.health;

import com.ycsopen.sms.core.domain.entity.Channel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;

public final class ChannelHealthTestSupport {
    private ChannelHealthTestSupport() { }

    public static JdbcTemplate jdbc(String name) {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + name + "-" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE channel_health_observations (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    channel_id BIGINT NOT NULL,
                    connected BOOLEAN NOT NULL,
                    timeout_rate DECIMAL(8,4) NOT NULL DEFAULT 0,
                    failure_rate DECIMAL(8,4) NOT NULL DEFAULT 0,
                    average_latency_ms BIGINT NOT NULL DEFAULT 0,
                    result_status VARCHAR(16) NOT NULL,
                    reason_code VARCHAR(64) NOT NULL,
                    observed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE channel_pause_events (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    channel_id BIGINT NOT NULL,
                    event_type VARCHAR(32) NOT NULL,
                    trigger_type VARCHAR(32) NOT NULL,
                    actor VARCHAR(64) NOT NULL,
                    reason VARCHAR(255) NOT NULL,
                    source_event_key VARCHAR(128) NOT NULL UNIQUE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE channel_pools (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    pool_name VARCHAR(64) NOT NULL UNIQUE,
                    mode VARCHAR(32) NOT NULL,
                    version BIGINT NOT NULL DEFAULT 0,
                    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE channel_pool_members (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    pool_id BIGINT NOT NULL,
                    channel_id BIGINT NOT NULL,
                    weight INT NOT NULL DEFAULT 0,
                    primary_member BOOLEAN NOT NULL DEFAULT FALSE,
                    enabled BOOLEAN NOT NULL DEFAULT TRUE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(pool_id, channel_id)
                )
                """);
        return jdbc;
    }

    public static Channel channel(long id, Channel.Status status) {
        Channel channel = new Channel();
        channel.setId(id);
        channel.setChannelName("channel-" + id);
        channel.setProtocol(Channel.Protocol.CMPP);
        channel.setOperator(Channel.Operator.MOBILE);
        channel.setStatus(status);
        channel.setAvailability("AVAILABLE");
        channel.setEffectiveVersionId(100L + id);
        channel.setPriority(50);
        channel.setPrice(BigDecimal.ONE);
        return channel;
    }
}
