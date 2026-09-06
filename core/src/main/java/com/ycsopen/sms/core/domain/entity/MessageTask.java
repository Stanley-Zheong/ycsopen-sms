package com.ycsopen.sms.core.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ycsopen.sms.core.common.security.persistence.MessageTaskProtectionAdapter;
import com.ycsopen.sms.core.common.security.persistence.PreparedMessageMobile;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

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

    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ToString.Exclude
    @Column(name = "mobile_encrypted", nullable = false, length = 255)
    private byte[] mobileEncrypted;

    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ToString.Exclude
    @Column(name = "mobile_hash", nullable = false, length = 64)
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

    /** Accepts only the adapter-produced, context-bound value. */
    public void assignPreparedMobile(PreparedMessageMobile prepared,
                                     MessageTaskProtectionAdapter.AssignmentPermit permit) {
        if (prepared == null || permit == null || mobileEncrypted != null || mobileHash != null) {
            throw new IllegalStateException("protected mobile assignment rejected");
        }
        mobileEncrypted = prepared.copyEnvelope();
        mobileHash = prepared.legacyLocator();
    }

    /** Clears only the exact prepared assignment after its transaction rolled back. */
    public void clearPreparedMobile(PreparedMessageMobile prepared,
                                    MessageTaskProtectionAdapter.AssignmentPermit permit) {
        if (permit != null && prepared != null
                && java.util.Arrays.equals(mobileEncrypted, prepared.copyEnvelope())
                && java.util.Objects.equals(mobileHash, prepared.legacyLocator())) {
            if (mobileEncrypted != null) {
                java.util.Arrays.fill(mobileEncrypted, (byte) 0);
            }
            mobileEncrypted = null;
            mobileHash = null;
        }
    }

    public boolean hasPreparedMobile() {
        return mobileEncrypted != null || mobileHash != null;
    }

    /** Existing plaintext callers fail closed until Plan 03-26 rewires them to the adapter. */
    @Deprecated(forRemoval = true)
    public void setMobileEncrypted(String rejectedPlaintext) {
        throw new IllegalStateException("protected mobile requires MessageTaskProtectionAdapter");
    }

    /** Existing raw-digest callers fail closed until Plan 03-26 rewires them to the adapter. */
    @Deprecated(forRemoval = true)
    public void setMobileHash(String rejectedDigest) {
        throw new IllegalStateException("protected mobile requires MessageTaskProtectionAdapter");
    }

    public enum SendStatus { PENDING, SENT, DELIVERED, FAILED }
}
