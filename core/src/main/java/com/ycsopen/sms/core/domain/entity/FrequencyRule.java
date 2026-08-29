package com.ycsopen.sms.core.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** F-5.6 频次拦截规则，同时承担"防刷机制"职责（见 PRD 6.2 节）。 */
@Entity
@Table(name = "frequency_rules")
@Getter
@Setter
public class FrequencyRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_name", nullable = false)
    private String ruleName;

    @Enumerated(EnumType.STRING)
    @Column(name = "limit_type", nullable = false)
    private LimitType limitType;

    @Column(name = "limit_count", nullable = false)
    private Integer limitCount;

    @Column(name = "limit_window_seconds", nullable = false)
    private Integer limitWindowSeconds;

    @Enumerated(EnumType.STRING)
    private Action action = Action.BLOCK;

    @Enumerated(EnumType.STRING)
    private Status status = Status.ACTIVE;

    public enum LimitType { MOBILE, TENANT_LEVEL, IP, CONTENT_SIMILARITY }
    public enum Action { BLOCK, DELAY, ALERT }
    public enum Status { ACTIVE, DISABLED }
}
