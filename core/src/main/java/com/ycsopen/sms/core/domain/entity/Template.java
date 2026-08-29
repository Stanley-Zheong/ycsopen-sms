package com.ycsopen.sms.core.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** F-3.4/F-3.5 模板。isSystemTemplate 对应 PRD 检视 Finding #1：平台系统消息（如注册验证码）走免审系统模板。 */
@Entity
@Table(name = "templates")
@Getter
@Setter
public class Template {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "template_code", nullable = false)
    private String templateCode;

    @Column(nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "template_type", nullable = false)
    private TemplateType templateType;

    @Column(name = "signature_id", nullable = false)
    private Long signatureId;

    @Enumerated(EnumType.STRING)
    @Column(name = "audit_status")
    private AuditStatus auditStatus = AuditStatus.PENDING;

    @Column(name = "is_system_template")
    private Boolean isSystemTemplate = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum TemplateType { VERIFY, NOTIFY, MARKETING }
    public enum AuditStatus { PENDING, APPROVED, REJECTED }
}
