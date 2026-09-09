package com.ycsopen.sms.core.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;

/** F-4 上游通道。状态机见 ycsansms.md 5.4 节：NORMAL <-> PAUSED（F-4.7）/ MAINTENANCE / ABNORMAL。 */
@Entity
@Table(name = "channels")
@Getter
@Setter
public class Channel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel_name", nullable = false)
    private String channelName;

    @Enumerated(EnumType.STRING)
    private Protocol protocol;

    @Enumerated(EnumType.STRING)
    private Operator operator;

    private String host;
    private Integer port;

    @JsonIgnore
    @Column(name = "account_encrypted")
    private byte[] accountEncrypted;

    @JsonIgnore
    @Column(name = "password_encrypted")
    private byte[] passwordEncrypted;

    @Column(name = "sp_id")
    private String spId;

    @Column(name = "service_id")
    private String serviceId;

    @Column(name = "src_id")
    private String srcId;

    @Column(name = "max_connections", nullable = false)
    private Integer maxConnections = 10;

    @Column(name = "window_size", nullable = false)
    private Integer windowSize = 8;

    @Column(name = "tps_limit", nullable = false)
    private Integer tpsLimit = 100;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer priority = 50;

    @Enumerated(EnumType.STRING)
    private Status status = Status.NORMAL;

    @Column(name = "pause_reason")
    private String pauseReason;

    @Column(name = "active_window")
    private String activeWindow;

    @Column(nullable = false)
    private String availability = "AVAILABLE";

    @Column(name = "extra_config")
    private String extraConfig;

    @Column(name = "effective_version_id")
    private Long effectiveVersionId;

    @Column(name = "configuration_version", nullable = false)
    private Long configurationVersion = 0L;

    @Column(name = "paused_by")
    private String pausedBy;

    @Column(name = "paused_at")
    private LocalDateTime pausedAt;

    @Column(name = "offline_by")
    private String offlineBy;

    @Column(name = "offline_at")
    private LocalDateTime offlineAt;

    public enum Protocol { CMPP, SGIP, SMGP, HTTP }
    public enum Operator { MOBILE, UNICOM, TELECOM, VIRTUAL, INTERNATIONAL }
    public enum Status { NORMAL, MAINTENANCE, ABNORMAL, PAUSED, OFFLINE }

    /** F-5.9：路由引擎只应该把消息投给"正常"状态的通道。 */
    public boolean isRoutable() {
        return status == Status.NORMAL && "AVAILABLE".equals(availability) && effectiveVersionId != null;
    }
}
