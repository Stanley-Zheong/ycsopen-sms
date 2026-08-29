package com.ycsopen.sms.core.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** F-3.1/F-3.2 签名。 */
@Entity
@Table(name = "signatures")
@Getter
@Setter
public class Signature {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "sign_code", nullable = false)
    private String signCode;

    @Column(name = "sign_content", nullable = false)
    private String signContent;

    @Enumerated(EnumType.STRING)
    @Column(name = "sign_type", nullable = false)
    private SignType signType;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level")
    private RiskLevel riskLevel = RiskLevel.LOW;

    @Enumerated(EnumType.STRING)
    @Column(name = "audit_status")
    private AuditStatus auditStatus = AuditStatus.PENDING;

    @Column(name = "audit_comment")
    private String auditComment;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum SignType { ENTERPRISE, APP, TRADEMARK, INSTITUTION, GOVERNMENT }
    public enum RiskLevel { LOW, MEDIUM, HIGH }
    public enum AuditStatus { PENDING, APPROVED, REJECTED }
}
