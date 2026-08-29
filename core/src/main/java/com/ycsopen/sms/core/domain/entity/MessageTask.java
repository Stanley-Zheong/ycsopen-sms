package com.ycsopen.sms.core.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * F-7.2 发送详单。状态机（PRD 5.7 节）：
 * PENDING -[受理成功]-> SENT -[回执:送达]-> DELIVERED
 * PENDING -[路由拦截/上游拒绝]-> FAILED -[命中重发规则]-> PENDING（重试）
 */
@Entity
@Table(name = "message_tasks")
@Getter
@Setter
public class MessageTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false, unique = true)
    private String messageId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "signature_id")
    private Long signatureId;

    @Column(name = "mobile_encrypted", nullable = false)
    private String mobileEncrypted;

    @Column(name = "mobile_hash", nullable = false)
    private String mobileHash;

    @Column(nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "send_status")
    private SendStatus sendStatus = SendStatus.PENDING;

    @Column(name = "channel_id")
    private Long channelId;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    private BigDecimal cost = BigDecimal.ZERO;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Version
    private Integer version;

    public enum SendStatus { PENDING, SENT, DELIVERED, FAILED }
}
