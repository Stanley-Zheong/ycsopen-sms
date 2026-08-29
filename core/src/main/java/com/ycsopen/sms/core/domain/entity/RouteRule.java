package com.ycsopen.sms.core.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** F-5.8 分流规则。tenantId 为 null 表示全局规则。 */
@Entity
@Table(name = "route_rules")
@Getter
@Setter
public class RouteRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_name", nullable = false)
    private String ruleName;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Enumerated(EnumType.STRING)
    private Channel.Operator operator;

    @Column(name = "phone_prefix")
    private String phonePrefix;

    @Column(name = "target_channel_id")
    private Long targetChannelId;

    @Column(nullable = false)
    private Integer priority = 1;

    @Enumerated(EnumType.STRING)
    private Status status = Status.ACTIVE;

    public enum Status { ACTIVE, DISABLED }
}
