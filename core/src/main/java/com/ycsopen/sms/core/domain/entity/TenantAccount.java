package com.ycsopen.sms.core.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** F-8.1 机构预付费账户；version 字段实现乐观锁防并发扣费错误。 */
@Entity
@Table(name = "tenant_accounts")
@Getter
@Setter
public class TenantAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private Long tenantId;

    /** 单位：厘（1 元 = 1000 厘），与参考实现 ycsan-sms 保持一致的最小货币单位。 */
    @Column(nullable = false)
    private Long balance = 0L;

    @Column(name = "frozen_amount", nullable = false)
    private Long frozenAmount = 0L;

    @Enumerated(EnumType.STRING)
    private Status status = Status.NORMAL;

    @Version
    private Integer version;

    public enum Status { NORMAL, DISABLED, ARREARS_FROZEN }
}
