package com.ycsopen.sms.core.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** CMPP access metadata; account and password are protected columns and never serialized. */
@Entity @Table(name = "tenant_protocol_credentials") @Getter @Setter
public class TenantProtocolCredential {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="tenant_id", nullable=false) private Long tenantId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Protocol protocol = Protocol.CMPP;
    private String spid;
    @Column(name="endpoint_host") private String endpointHost;
    @Column(name="endpoint_port") private Integer endpointPort;
    @Column(name="ip_whitelist") private String ipWhitelist;
    @Column(name="max_connections") private Integer maxConnections = 4;
    @Column(name="tps_limit") private Integer tpsLimit = 100;
    @Column(name="window_size") private Integer windowSize = 8;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;
    @Column(name="revoked_at") private java.time.LocalDateTime revokedAt;

    public enum Protocol { CMPP }
    public enum Status { ACTIVE, DISABLED }
}
