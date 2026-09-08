package com.ycsopen.sms.core.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

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

    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ToString.Exclude
    @Column(name = "app_secret_encrypted", nullable = false, length = 255)
    private byte[] appSecretEncrypted;

    @Column(name = "key_name")
    private String keyName;

    private String description;

    @Column(name = "ip_whitelist")
    private String ipWhitelist;

    @Enumerated(EnumType.STRING)
    private Status status = Status.ACTIVE;

    @Column(name = "rate_limit_per_sec")
    private Integer rateLimitPerSec = 10;

    @Column(name = "rate_limit_per_min")
    private Integer rateLimitPerMin = 100;
    @Column(name = "rate_limit_per_hour")
    private Integer rateLimitPerHour = 1000;
    @Column(name = "rate_limit_per_day")
    private Integer rateLimitPerDay = 10000;
    @Column(name = "expire_time")
    private java.time.LocalDateTime expireTime;
    @Column(name = "last_used_time")
    private java.time.LocalDateTime lastUsedTime;
    @Column(name = "revoked_at")
    private java.time.LocalDateTime revokedAt;

    public enum Status { ACTIVE, DISABLED }
}
