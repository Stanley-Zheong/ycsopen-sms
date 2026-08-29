package com.ycsopen.sms.core.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer priority = 50;

    @Enumerated(EnumType.STRING)
    private Status status = Status.NORMAL;

    @Column(name = "pause_reason")
    private String pauseReason;

    @Column(name = "paused_by")
    private String pausedBy;

    @Column(name = "paused_at")
    private LocalDateTime pausedAt;

    public enum Protocol { CMPP, SGIP, SMGP, HTTP }
    public enum Operator { MOBILE, UNICOM, TELECOM, VIRTUAL, INTERNATIONAL }
    public enum Status { NORMAL, MAINTENANCE, ABNORMAL, PAUSED }

    /** F-5.9：路由引擎只应该把消息投给"正常"状态的通道。 */
    public boolean isRoutable() {
        return status == Status.NORMAL;
    }
}
