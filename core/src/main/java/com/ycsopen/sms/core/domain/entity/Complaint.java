package com.ycsopen.sms.core.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** F-9.1 投诉工单。channelId 字段是本次 PRD 增补的新字段，用于驱动 F-11.9 通道投诉占比统计。 */
@Entity
@Table(name = "complaints")
@Getter
@Setter
public class Complaint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private Source source;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "channel_id")
    private Long channelId;

    @Column(name = "message_id")
    private String messageId;

    private String summary;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum Source { REGULATOR, OPERATOR, USER_REPORT }
    public enum Status { PENDING, PROCESSING, PROCESSED, CLOSED }
}
