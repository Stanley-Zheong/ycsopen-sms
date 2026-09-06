package com.ycsopen.sms.core.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/** F-5.2 黑白名单。tenantId 为 null = 系统级（全平台生效），非 null = 机构级。 */
@Entity
@Table(name = "blacklist_entries")
@Getter
@Setter
public class BlacklistEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ToString.Exclude
    @Column(name = "mobile_encrypted", nullable = false, length = 255)
    private byte[] mobileEncrypted;

    @Column(name = "mobile_hash", nullable = false)
    private String mobileHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "list_type", nullable = false)
    private ListType listType = ListType.BLACK;

    private String reason;

    @Enumerated(EnumType.STRING)
    private Source source;

    @Enumerated(EnumType.STRING)
    private Status status = Status.ACTIVE;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum ListType { BLACK, WHITE }
    public enum Source { MANUAL, BATCH_IMPORT, UNSUBSCRIBE_AUTO, THIRD_PARTY_RISK, COMPLAINT_LINKED }
    public enum Status { ACTIVE, DISABLED }
}
