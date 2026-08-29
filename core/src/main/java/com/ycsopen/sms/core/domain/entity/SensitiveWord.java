package com.ycsopen.sms.core.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** F-5.5 内容审核词库（区别于 F-3 的资质/资源审核，见 PRD 5.3 节说明）。 */
@Entity
@Table(name = "sensitive_words")
@Getter
@Setter
public class SensitiveWord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String word;

    @Enumerated(EnumType.STRING)
    private Category category;

    @Enumerated(EnumType.STRING)
    private Level level = Level.MEDIUM;

    private String replacement;

    @Enumerated(EnumType.STRING)
    private Action action = Action.BLOCK;

    @Enumerated(EnumType.STRING)
    private Scope scope = Scope.GLOBAL;

    @Enumerated(EnumType.STRING)
    private Status status = Status.ACTIVE;

    @Column(name = "hit_count")
    private Long hitCount = 0L;

    public enum Category { ILLEGAL, FINANCIAL, MARKETING, POLITICAL, ADULT, OTHER }
    public enum Level { HIGH, MEDIUM, LOW }
    public enum Action { BLOCK, REPLACE, ALERT }
    public enum Scope { GLOBAL, TENANT, PRODUCT }
    public enum Status { ACTIVE, DISABLED }
}
