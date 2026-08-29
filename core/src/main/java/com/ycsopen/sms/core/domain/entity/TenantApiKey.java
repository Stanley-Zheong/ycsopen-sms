package com.ycsopen.sms.core.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** F-2.6/F-6.4 HTTP API 凭证。 */
@Entity
@Table(name = "tenant_api_keys")
@Getter
@Setter
public class TenantApiKey {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "app_key", nullable = false, unique = true)
    private String appKey;

    @Column(name = "app_secret_encrypted", nullable = false)
    private String appSecretEncrypted;

    @Enumerated(EnumType.STRING)
    private Status status = Status.ACTIVE;

    @Column(name = "rate_limit_per_sec")
    private Integer rateLimitPerSec = 10;

    public enum Status { ACTIVE, DISABLED }
}
