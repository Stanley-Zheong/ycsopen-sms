-- ============================================================================
-- ycsopen-sms core schema
-- Source of truth: Documents/优创硕安/系统开发/ycsansms.md, Chapter 10 (数据模型与数据字典)
-- Every table below is annotated with the PRD data-dictionary section it
-- implements, so a reviewer can trace schema <-> requirement 1:1.
-- Engine: MySQL 8.0, InnoDB, utf8mb4.
-- Fields marked "encrypted at application layer" correspond to PRD 6.2.1 —
-- MySQL stores ciphertext (VARBINARY/TEXT); encryption/decryption happens in
-- the service layer (see core/src/main/java/.../common/security), never in SQL.
-- ============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------------
-- 10.1 账号与权限数据项 (F-1)
-- ----------------------------------------------------------------------------
CREATE TABLE users (
    id                    BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    username              VARCHAR(50)  NOT NULL,
    password_hash         VARCHAR(100) NOT NULL COMMENT 'bcrypt/Argon2, never MD5 (see PRD 6.2.1)',
    email                 VARCHAR(100),
    phone_encrypted       VARBINARY(255) COMMENT '🔒 AES-256 envelope-encrypted, see PRD 6.2.1',
    real_name             VARCHAR(50),
    avatar_url            VARCHAR(200),
    user_type             ENUM('ADMIN','OPERATOR','FINANCE','TENANT_ADMIN','TENANT_USER','TENANT_DEV') NOT NULL,
    tenant_id             BIGINT UNSIGNED NULL COMMENT '机构用户必填，见 F-1.3',
    status                ENUM('ACTIVE','DISABLED','LOCKED') NOT NULL DEFAULT 'ACTIVE',
    last_login_time       DATETIME NULL,
    last_login_ip         VARCHAR(45),
    failed_login_count    INT NOT NULL DEFAULT 0,
    password_expire_time  DATETIME NULL,
    created_by            VARCHAR(50),
    created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_username (username),
    KEY idx_tenant (tenant_id),
    KEY idx_user_type (user_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-1.1/F-1.3 平台与机构用户统一账号表';

CREATE TABLE roles (
    id          BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    role_code   VARCHAR(50)  NOT NULL,
    role_name   VARCHAR(100) NOT NULL,
    description TEXT,
    role_type   ENUM('PLATFORM','TENANT') NOT NULL,
    tenant_id   BIGINT UNSIGNED NULL,
    status      ENUM('ACTIVE','DISABLED') NOT NULL DEFAULT 'ACTIVE',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-1.2 角色';

CREATE TABLE permissions (
    id              BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    permission_code VARCHAR(100) NOT NULL,
    permission_name VARCHAR(100) NOT NULL,
    resource_type   ENUM('MENU','BUTTON','API','DATA') NOT NULL,
    resource_path   VARCHAR(200),
    http_method     VARCHAR(10),
    parent_id       BIGINT UNSIGNED NULL,
    sort_order      INT NOT NULL DEFAULT 0,
    status          ENUM('ACTIVE','DISABLED') NOT NULL DEFAULT 'ACTIVE',
    UNIQUE KEY uk_permission_code (permission_code),
    KEY idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-1.2 权限点';

CREATE TABLE user_roles (
    user_id  BIGINT UNSIGNED NOT NULL,
    role_id  BIGINT UNSIGNED NOT NULL,
    granted_by VARCHAR(50),
    granted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME NULL,
    PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE role_permissions (
    role_id       BIGINT UNSIGNED NOT NULL,
    permission_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_sessions (
    id               VARCHAR(128) PRIMARY KEY,
    user_id          BIGINT UNSIGNED NOT NULL,
    user_type        VARCHAR(20) NOT NULL,
    tenant_id        BIGINT UNSIGNED NULL,
    login_ip         VARCHAR(45) NOT NULL,
    user_agent       VARCHAR(512),
    login_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_access_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    expires_at       DATETIME NOT NULL,
    is_abnormal_login TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'F-14.2 安全审计标记',
    KEY idx_user (user_id),
    KEY idx_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- 10.2 机构档案与生命周期数据项 (F-2, 第12章生命周期)
-- ----------------------------------------------------------------------------
CREATE TABLE tenants (
    id                        BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    tenant_no                 VARCHAR(32)  NOT NULL COMMENT '机构编号，对外可见',
    short_name                VARCHAR(20)  NOT NULL COMMENT '企业简称',
    full_name                 VARCHAR(100) NOT NULL COMMENT '企业全称',
    unified_social_credit_code VARCHAR(18) NOT NULL,
    business_license_url      VARCHAR(255) COMMENT '🔒 存对象存储加密桶的引用，非明文文件',
    legal_rep_name             VARCHAR(50),
    legal_rep_id_no_encrypted  VARBINARY(255) COMMENT '🔒 身份证号',
    legal_rep_id_front_url     VARCHAR(255) COMMENT '🔒',
    legal_rep_id_back_url      VARCHAR(255) COMMENT '🔒',
    contact_name               VARCHAR(50),
    contact_id_no_encrypted    VARBINARY(255) COMMENT '🔒 [新增] 联系人身份证号',
    contact_phone_encrypted    VARBINARY(255) COMMENT '🔒',
    registered_capital         VARCHAR(50),
    business_scope             TEXT,
    registered_address         VARCHAR(255),
    business_address           VARCHAR(255),
    license_valid_until        DATE,
    shortlink_domain_proof_url VARCHAR(255) COMMENT '[新增] 短链域名所有权证明',
    trademark_proof_url        VARCHAR(255) COMMENT '[新增] 商标所有权证明',
    customer_level             TINYINT NOT NULL DEFAULT 1,
    biz_manager                VARCHAR(64),
    industry                   VARCHAR(64),
    -- 认证状态机 (PRD 5.2 状态机)
    verification_status        ENUM('UNVERIFIED','PENDING','VERIFIED','REJECTED') NOT NULL DEFAULT 'UNVERIFIED',
    verification_time          DATETIME NULL,
    verification_updated_at    DATETIME NULL,
    -- 生命周期状态机 (F-2.8/F-2.9/F-2.10)
    lifecycle_status           ENUM('SUBMITTED','TRIAL','TRIAL_FROZEN','SIGNED','FROZEN','TERMINATED') NOT NULL DEFAULT 'SUBMITTED',
    -- 试用信息 [新增] F-2.8
    trial_quota                INT NULL,
    trial_quota_used           INT NOT NULL DEFAULT 0,
    trial_start_at             DATETIME NULL,
    trial_end_at                DATETIME NULL,
    trial_expiry_action        ENUM('AUTO_FREEZE','AUTO_CONVERT') NOT NULL DEFAULT 'AUTO_FREEZE',
    -- 签约信息 [新增] F-2.9
    billing_mode                ENUM('PREPAID','POSTPAID') NULL,
    price_plan_version          VARCHAR(32) NULL,
    credit_limit                BIGINT NULL COMMENT '后付费授信额度，单位：厘',
    billing_cycle                ENUM('MONTHLY','QUARTERLY') NULL,
    contract_no                  VARCHAR(64) NULL,
    contract_signed_at           DATE NULL,
    contract_attachment_url      VARCHAR(255) NULL,
    signed_by                    VARCHAR(64) NULL,
    -- 终止信息 [新增] F-2.10
    termination_requested_by     VARCHAR(64) NULL,
    termination_reason           ENUM('VOLUNTARY','SEVERE_VIOLATION','LONG_TERM_ARREARS','EXPIRED_CREDENTIALS') NULL,
    termination_approved_by      VARCHAR(64) NULL,
    termination_approved_at      DATETIME NULL,
    termination_effective_date   DATE NULL,
    termination_settlement_status ENUM('PENDING','SETTLED') NULL,
    created_by                    VARCHAR(64),
    created_at                    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_no (tenant_no),
    UNIQUE KEY uk_credit_code (unified_social_credit_code),
    KEY idx_lifecycle_status (lifecycle_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-2 机构主档 + 生命周期字段';

CREATE TABLE tenant_accounts (
    id              BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    tenant_id       BIGINT UNSIGNED NOT NULL,
    balance         BIGINT NOT NULL DEFAULT 0 COMMENT '预付费余额，单位：厘',
    frozen_amount   BIGINT NOT NULL DEFAULT 0,
    status          ENUM('NORMAL','DISABLED','ARREARS_FROZEN') NOT NULL DEFAULT 'NORMAL',
    version         INT NOT NULL DEFAULT 0 COMMENT '乐观锁，防并发扣费错误',
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-8.1 机构账户';

CREATE TABLE tenant_configs (
    id           BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    tenant_id    BIGINT UNSIGNED NOT NULL,
    config_key   VARCHAR(128) NOT NULL,
    config_value TEXT,
    description  VARCHAR(255),
    updated_by   VARCHAR(64),
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_key (tenant_id, config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- 10.3 签名与模板数据项 (F-3)
-- ----------------------------------------------------------------------------
CREATE TABLE signatures (
    id                    BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    tenant_id             BIGINT UNSIGNED NOT NULL,
    biz_type              ENUM('DOMESTIC','INTERNATIONAL') NOT NULL DEFAULT 'DOMESTIC',
    sign_code             VARCHAR(32) NOT NULL,
    sign_content           VARCHAR(64) NOT NULL,
    sign_type              ENUM('ENTERPRISE','APP','TRADEMARK','INSTITUTION','GOVERNMENT') NOT NULL,
    usage_type              ENUM('SELF','OTHER') NOT NULL DEFAULT 'SELF',
    risk_level              ENUM('LOW','MEDIUM','HIGH') NOT NULL DEFAULT 'LOW',
    evidence_url            VARCHAR(255),
    applicant_name          VARCHAR(50),
    applicant_phone_encrypted VARBINARY(255) COMMENT '🔒',
    applicant_id_no_encrypted VARBINARY(255) COMMENT '🔒',
    audit_status             ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
    audit_time                DATETIME NULL,
    audit_comment             VARCHAR(500),
    created_at                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_signcode (tenant_id, sign_code),
    KEY idx_audit_status (audit_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-3.1/F-3.2 签名';

CREATE TABLE signature_channel_registrations (
    id            BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    signature_id  BIGINT UNSIGNED NOT NULL,
    channel_id    BIGINT UNSIGNED NOT NULL,
    reg_status    ENUM('NONE','REGISTERING','REGISTERED','FAILED') NOT NULL DEFAULT 'NONE',
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sign_channel (signature_id, channel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-3.3 签名通道报备状态';

CREATE TABLE templates (
    id               BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    tenant_id        BIGINT UNSIGNED NOT NULL,
    biz_type         ENUM('DOMESTIC','INTERNATIONAL') NOT NULL DEFAULT 'DOMESTIC',
    template_code    VARCHAR(32) NOT NULL,
    template_name    VARCHAR(50) NOT NULL,
    template_type    ENUM('VERIFY','NOTIFY','MARKETING') NOT NULL,
    content          VARCHAR(500) NOT NULL COMMENT '含 ${var} 占位符',
    signature_id     BIGINT UNSIGNED NOT NULL,
    param_check_rule VARCHAR(255),
    description      VARCHAR(255),
    audit_status     ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
    audit_time       DATETIME NULL,
    audit_comment    VARCHAR(500),
    usage_count      BIGINT NOT NULL DEFAULT 0,
    is_system_template TINYINT(1) NOT NULL DEFAULT 0 COMMENT '平台系统消息模板（注册验证码等），免审——见 PRD 检视 Finding #1',
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_templatecode (tenant_id, template_code),
    KEY idx_audit_status (audit_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-3.4/F-3.5 模板';

CREATE TABLE exempt_rules (
    id            BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    tenant_id     BIGINT UNSIGNED NOT NULL,
    exempt_type   ENUM('SIGNATURE','CONTENT','ACCOUNT') NOT NULL,
    scope         VARCHAR(255),
    valid_until   DATETIME,
    approved_by   VARCHAR(64),
    usage_count   BIGINT NOT NULL DEFAULT 0,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-3.6 免审规则';

-- ----------------------------------------------------------------------------
-- 10.4 通道与路由数据项 (F-4, F-5)
-- ----------------------------------------------------------------------------
CREATE TABLE channels (
    id                BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    channel_name      VARCHAR(64) NOT NULL,
    protocol          ENUM('CMPP','SGIP','SMGP','HTTP') NOT NULL,
    operator          ENUM('MOBILE','UNICOM','TELECOM','VIRTUAL','INTERNATIONAL') NOT NULL,
    host              VARCHAR(128),
    port              INT,
    account_encrypted  VARBINARY(255) COMMENT '🔒',
    password_encrypted VARBINARY(255) COMMENT '🔒',
    sp_id              VARCHAR(32),
    service_id         VARCHAR(16),
    src_id             VARCHAR(32),
    max_connections    INT NOT NULL DEFAULT 10,
    window_size        INT NOT NULL DEFAULT 8,
    price              DECIMAL(10,4) NOT NULL DEFAULT 0,
    priority           INT NOT NULL DEFAULT 50,
    active_window      VARCHAR(64),
    extra_config       JSON,
    status             ENUM('NORMAL','MAINTENANCE','ABNORMAL','PAUSED') NOT NULL DEFAULT 'NORMAL',
    pause_reason       VARCHAR(255) NULL COMMENT '[新增] F-4.7',
    paused_by          VARCHAR(64) NULL,
    paused_at          DATETIME NULL,
    resumed_at         DATETIME NULL,
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_operator_status (operator, status),
    KEY idx_priority (priority DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-4 上游通道';

CREATE TABLE channel_groups (
    id           BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    group_name   VARCHAR(64) NOT NULL,
    strategy     ENUM('WEIGHT','PRIMARY_BACKUP') NOT NULL DEFAULT 'WEIGHT',
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-4.6 通道池';

CREATE TABLE channel_group_members (
    group_id    BIGINT UNSIGNED NOT NULL,
    channel_id  BIGINT UNSIGNED NOT NULL,
    weight      INT NOT NULL DEFAULT 1,
    is_primary  TINYINT(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (group_id, channel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE route_rules (
    id                BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    rule_name         VARCHAR(100) NOT NULL,
    tenant_id         BIGINT UNSIGNED NULL COMMENT 'NULL = 全局规则',
    operator          ENUM('MOBILE','UNICOM','TELECOM') NULL,
    phone_prefix      VARCHAR(20) NULL,
    content_keyword   VARCHAR(100) NULL,
    time_range        VARCHAR(50) NULL,
    target_channel_id BIGINT UNSIGNED NULL,
    target_group_id   BIGINT UNSIGNED NULL,
    priority          INT NOT NULL DEFAULT 1,
    status            ENUM('ACTIVE','DISABLED') NOT NULL DEFAULT 'ACTIVE',
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_tenant (tenant_id),
    KEY idx_priority (priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-5.8 分流规则';

CREATE TABLE retry_rules (
    id               BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    error_code       VARCHAR(32) NOT NULL,
    retry_interval_seconds INT NOT NULL DEFAULT 60,
    max_retry_count  INT NOT NULL DEFAULT 3,
    status           ENUM('ACTIVE','DISABLED') NOT NULL DEFAULT 'ACTIVE'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-5.10 重发规则';

CREATE TABLE mobile_locations (
    mobile_prefix VARCHAR(7) PRIMARY KEY,
    operator      ENUM('MOBILE','UNICOM','TELECOM') NOT NULL,
    province      VARCHAR(32) NOT NULL,
    city          VARCHAR(32) NOT NULL,
    area_code     VARCHAR(8),
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-5.7 号码归属地库';

CREATE TABLE mobile_portability (
    mobile_encrypted   VARBINARY(255) NOT NULL COMMENT '🔒',
    mobile_hash        CHAR(64) NOT NULL COMMENT 'SHA-256(mobile)，用于加密字段的等值查询',
    original_operator  ENUM('MOBILE','UNICOM','TELECOM') NOT NULL,
    current_operator   ENUM('MOBILE','UNICOM','TELECOM') NOT NULL,
    ported_at          DATE,
    updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (mobile_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-5.7 携号转网库';

-- ----------------------------------------------------------------------------
-- 10.5 风控与内容安全数据项 (F-5.1~F-5.6)
-- ----------------------------------------------------------------------------
CREATE TABLE blacklist_entries (
    id          BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    tenant_id   BIGINT UNSIGNED NULL COMMENT 'NULL = 系统级黑名单',
    mobile_encrypted VARBINARY(255) NOT NULL COMMENT '🔒',
    mobile_hash CHAR(64) NOT NULL,
    list_type   ENUM('BLACK','WHITE') NOT NULL DEFAULT 'BLACK',
    reason      VARCHAR(255),
    source      ENUM('MANUAL','BATCH_IMPORT','UNSUBSCRIBE_AUTO','THIRD_PARTY_RISK','COMPLAINT_LINKED') NOT NULL,
    status      ENUM('ACTIVE','DISABLED') NOT NULL DEFAULT 'ACTIVE',
    created_by  VARCHAR(64),
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_mobile_type (tenant_id, mobile_hash, list_type),
    KEY idx_mobile_hash (mobile_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-5.2 黑白名单（系统级 tenant_id 为空，机构级不为空）';

CREATE TABLE third_party_risk_check_logs (
    id               BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    request_id       VARCHAR(64) NOT NULL,
    mobile_hash      CHAR(64) NOT NULL,
    check_level      TINYINT NOT NULL DEFAULT 1,
    threshold_score  TINYINT NOT NULL DEFAULT 85,
    is_hit           TINYINT(1) NOT NULL,
    hit_source       TINYINT NULL COMMENT '1风险 2行为预测 3用户反馈 4靠号 5高敏',
    response_time_ms INT,
    degraded         TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'F-5.3 超时降级判断',
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_mobile_hash (mobile_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-5.3 第三方风险名单调用记录';

CREATE TABLE sensitive_words (
    id           BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    word         VARCHAR(128) NOT NULL,
    category     ENUM('ILLEGAL','FINANCIAL','MARKETING','POLITICAL','ADULT','OTHER') NOT NULL,
    level        ENUM('HIGH','MEDIUM','LOW') NOT NULL DEFAULT 'MEDIUM',
    replacement  VARCHAR(128),
    action       ENUM('BLOCK','REPLACE','ALERT') NOT NULL DEFAULT 'BLOCK',
    scope        ENUM('GLOBAL','TENANT','PRODUCT') NOT NULL DEFAULT 'GLOBAL',
    scope_ref_id BIGINT UNSIGNED NULL,
    status       ENUM('ACTIVE','DISABLED') NOT NULL DEFAULT 'ACTIVE',
    hit_count    BIGINT NOT NULL DEFAULT 0,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_word (word),
    KEY idx_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-5.5 内容审核词库';

CREATE TABLE frequency_rules (
    id            BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    rule_name     VARCHAR(100) NOT NULL,
    limit_type    ENUM('MOBILE','TENANT_LEVEL','IP','CONTENT_SIMILARITY') NOT NULL,
    limit_count   INT NOT NULL,
    limit_window_seconds INT NOT NULL,
    action        ENUM('BLOCK','DELAY','ALERT') NOT NULL DEFAULT 'BLOCK',
    scope         VARCHAR(64),
    status        ENUM('ACTIVE','DISABLED') NOT NULL DEFAULT 'ACTIVE',
    hit_count     BIGINT NOT NULL DEFAULT 0,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-5.6 频次拦截规则';

-- ----------------------------------------------------------------------------
-- 10.6 下游接入凭证与回调数据项 (F-2.6, F-6)
-- ----------------------------------------------------------------------------
CREATE TABLE tenant_api_keys (
    id              BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    tenant_id       BIGINT UNSIGNED NOT NULL,
    app_key         VARCHAR(64) NOT NULL,
    app_secret_encrypted VARBINARY(255) NOT NULL COMMENT '🔒',
    key_name        VARCHAR(64),
    status          ENUM('ACTIVE','DISABLED') NOT NULL DEFAULT 'ACTIVE',
    ip_whitelist    JSON,
    rate_limit_per_sec INT NOT NULL DEFAULT 10,
    rate_limit_per_min INT NOT NULL DEFAULT 100,
    rate_limit_per_hour INT NOT NULL DEFAULT 1000,
    rate_limit_per_day  INT NOT NULL DEFAULT 10000,
    expire_time     DATETIME NULL,
    last_used_time  DATETIME NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_app_key (app_key),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-2.6/F-6.4 HTTP API 凭证';

CREATE TABLE tenant_protocol_credentials (
    id               BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    tenant_id        BIGINT UNSIGNED NOT NULL,
    protocol         ENUM('CMPP') NOT NULL DEFAULT 'CMPP',
    account_encrypted VARBINARY(255) NOT NULL COMMENT '🔒',
    password_encrypted VARBINARY(255) NOT NULL COMMENT '🔒',
    ip_whitelist     JSON,
    max_connections  INT NOT NULL DEFAULT 4,
    tps_limit        INT NOT NULL DEFAULT 100,
    window_size      INT NOT NULL DEFAULT 8,
    status           ENUM('ACTIVE','DISABLED') NOT NULL DEFAULT 'ACTIVE',
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-2.6/F-6.7 CMPP 下游凭证';

CREATE TABLE tenant_callback_configs (
    tenant_id            BIGINT UNSIGNED PRIMARY KEY,
    delivery_callback_url VARCHAR(255),
    uplink_callback_url    VARCHAR(255),
    unsubscribe_callback_url VARCHAR(255),
    retry_max_count        INT NOT NULL DEFAULT 5,
    retry_backoff_seconds   INT NOT NULL DEFAULT 30
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-6.6 Webhook 回调配置';

-- ----------------------------------------------------------------------------
-- 10.7 消息与详单数据项 (F-7, F-10)
-- ----------------------------------------------------------------------------
CREATE TABLE message_submits (
    id               BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    submit_id        VARCHAR(64) NOT NULL COMMENT '对外暴露的业务流水号，幂等键',
    tenant_id        BIGINT UNSIGNED NOT NULL,
    source_protocol  ENUM('HTTP','CMPP') NOT NULL,
    product_type     ENUM('VERIFY','NOTIFY','MARKETING','INTERNATIONAL') NOT NULL,
    signature_id     BIGINT UNSIGNED NULL,
    template_id      BIGINT UNSIGNED NULL,
    status           ENUM('ACCEPTED','REJECTED','QUEUED') NOT NULL,
    reject_reason    VARCHAR(255) NULL COMMENT '如命中黑名单/内容审核/频控',
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_submit (tenant_id, submit_id),
    KEY idx_tenant_time (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-7.1 提交详单';

CREATE TABLE message_tasks (
    id               BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    message_id       VARCHAR(64) NOT NULL COMMENT '消息ID，对外暴露',
    submit_id        BIGINT UNSIGNED NULL,
    tenant_id        BIGINT UNSIGNED NOT NULL,
    template_id      BIGINT UNSIGNED NULL,
    signature_id     BIGINT UNSIGNED NULL,
    mobile_encrypted VARBINARY(255) NOT NULL COMMENT '🔒',
    mobile_hash      CHAR(64) NOT NULL,
    content          VARCHAR(600) NOT NULL,
    send_status      ENUM('PENDING','SENT','DELIVERED','FAILED') NOT NULL DEFAULT 'PENDING',
    channel_id       BIGINT UNSIGNED NULL,
    channel_msg_id   VARCHAR(64) NULL,
    operator         ENUM('MOBILE','UNICOM','TELECOM') NULL,
    province         VARCHAR(32),
    city             VARCHAR(32),
    error_code       VARCHAR(16),
    error_message    VARCHAR(255),
    cost             DECIMAL(10,4) NOT NULL DEFAULT 0,
    retry_count      INT NOT NULL DEFAULT 0,
    send_time        DATETIME NULL,
    deliver_time     DATETIME NULL,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version          INT NOT NULL DEFAULT 1,
    UNIQUE KEY uk_message_id (message_id),
    KEY idx_tenant_time (tenant_id, created_at),
    KEY idx_mobile_hash_time (mobile_hash, created_at),
    KEY idx_status_time (send_status, created_at),
    KEY idx_channel_time (channel_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-7.2 发送详单（分区候选表，按 created_at 月分区，见 PRD 10.12）';

CREATE TABLE bulk_sendings (
    id               BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    tenant_id        BIGINT UNSIGNED NOT NULL,
    task_name        VARCHAR(128) NOT NULL,
    message_type     ENUM('VERIFY','NOTIFY','MARKETING') NOT NULL,
    priority         ENUM('LOW','NORMAL','HIGH') NOT NULL DEFAULT 'NORMAL',
    template_id      BIGINT UNSIGNED NULL,
    signature_id     BIGINT UNSIGNED NULL,
    total_count      INT NOT NULL DEFAULT 0,
    success_count    INT NOT NULL DEFAULT 0,
    fail_count       INT NOT NULL DEFAULT 0,
    task_status      ENUM('PENDING','RUNNING','PAUSED','COMPLETED','FAILED') NOT NULL DEFAULT 'PENDING',
    schedule_time    DATETIME NULL,
    start_time       DATETIME NULL,
    end_time         DATETIME NULL,
    total_cost       DECIMAL(12,4) NOT NULL DEFAULT 0,
    created_by       VARCHAR(64),
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_tenant_status (tenant_id, task_status),
    KEY idx_schedule (schedule_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-7.3 批量（群发）任务';

CREATE TABLE bulk_sending_items (
    id             BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    bulk_id        BIGINT UNSIGNED NOT NULL,
    mobile_encrypted VARBINARY(255) NOT NULL COMMENT '🔒',
    template_params JSON,
    send_status    ENUM('PENDING','SENT','FAILED') NOT NULL DEFAULT 'PENDING',
    send_time      DATETIME NULL,
    error_message  VARCHAR(255),
    KEY idx_bulk (bulk_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE delivery_reports (
    id             BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    message_id     VARCHAR(64) NOT NULL,
    channel_id     BIGINT UNSIGNED NULL,
    upstream_msg_id VARCHAR(64) NULL,
    report_status  ENUM('DELIVERED','FAILED') NOT NULL,
    error_code     VARCHAR(16),
    raw_payload    TEXT,
    report_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_message (message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-7.4 回执/回单原始记录';

CREATE TABLE uplink_records (
    id             BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    tenant_id      BIGINT UNSIGNED NULL,
    mobile_encrypted VARBINARY(255) NOT NULL COMMENT '🔒',
    content        VARCHAR(500) NOT NULL,
    target_number  VARCHAR(32),
    channel_id     BIGINT UNSIGNED NULL,
    location       VARCHAR(64),
    is_unsubscribe TINYINT(1) NOT NULL DEFAULT 0,
    push_status    ENUM('NOT_PUSHED','SUCCESS','FAILED') NOT NULL DEFAULT 'NOT_PUSHED',
    push_time      DATETIME NULL,
    push_url       VARCHAR(255),
    received_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_tenant_time (tenant_id, received_at),
    KEY idx_push_status (push_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-7.5/F-10 上行详单';

CREATE TABLE unsubscribe_records (
    id                BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    mobile_encrypted  VARBINARY(255) NOT NULL COMMENT '🔒',
    mobile_hash       CHAR(64) NOT NULL,
    unsubscribed_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    trigger_keyword   VARCHAR(32),
    tenant_id         BIGINT UNSIGNED NOT NULL,
    signature_id      BIGINT UNSIGNED NULL,
    result            ENUM('TENANT_BLACKLISTED','SYSTEM_BLACKLISTED') NOT NULL DEFAULT 'TENANT_BLACKLISTED',
    confirmed_reply_sent TINYINT(1) NOT NULL DEFAULT 0,
    notified_tenant   TINYINT(1) NOT NULL DEFAULT 0,
    uplink_record_id  BIGINT UNSIGNED NULL,
    KEY idx_tenant_time (tenant_id, unsubscribed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-7.9/F-10.2 [新增] 退订记录';

CREATE TABLE unsubscribe_keywords (
    id       BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    keyword  VARCHAR(32) NOT NULL,
    scope    ENUM('GLOBAL','TENANT') NOT NULL DEFAULT 'GLOBAL',
    tenant_id BIGINT UNSIGNED NULL,
    status   ENUM('ACTIVE','DISABLED') NOT NULL DEFAULT 'ACTIVE'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-10.2 [新增] 退订关键词库';

CREATE TABLE export_tasks (
    id           BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    export_type  VARCHAR(64) NOT NULL COMMENT '发送/错误/群发/机构统计/对账单等',
    created_by   VARCHAR(64),
    file_format  ENUM('EXCEL','CSV','JSON','PDF') NOT NULL,
    status       ENUM('PENDING','RUNNING','COMPLETED','FAILED') NOT NULL DEFAULT 'PENDING',
    progress_pct TINYINT NOT NULL DEFAULT 0,
    record_count BIGINT NOT NULL DEFAULT 0,
    file_size_bytes BIGINT NULL,
    file_url     VARCHAR(255) NULL COMMENT '加密存储，见 PRD 6.2.1',
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-7.8 导出任务';

-- ----------------------------------------------------------------------------
-- 10.8 计费与账务数据项 (F-8)
-- ----------------------------------------------------------------------------
CREATE TABLE recharge_records (
    id             BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    tenant_id      BIGINT UNSIGNED NOT NULL,
    amount         BIGINT NOT NULL COMMENT '单位：厘',
    recharge_type  ENUM('MANUAL','AUTO') NOT NULL DEFAULT 'MANUAL',
    payment_method VARCHAR(32),
    trade_no       VARCHAR(64),
    status         ENUM('PENDING','SUCCESS','FAILED') NOT NULL DEFAULT 'PENDING',
    operator       VARCHAR(64),
    remark         VARCHAR(255),
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-8.3 充值记录';

CREATE TABLE expend_records (
    id            BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    tenant_id     BIGINT UNSIGNED NOT NULL,
    amount        BIGINT NOT NULL,
    expend_type   ENUM('SMS','OTHER') NOT NULL DEFAULT 'SMS',
    business_id   VARCHAR(64),
    business_type VARCHAR(32),
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_tenant_business (tenant_id, business_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-8.4 消费流水';

CREATE TABLE billing_records (
    id            BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    tenant_id     BIGINT UNSIGNED NOT NULL,
    task_ref_id   BIGINT UNSIGNED NOT NULL COMMENT '关联 message_tasks.id',
    channel_id    BIGINT UNSIGNED NULL,
    unit_price    DECIMAL(10,4) NOT NULL,
    quantity      INT NOT NULL DEFAULT 1,
    amount        BIGINT NOT NULL COMMENT '厘',
    billing_status ENUM('RESERVED','CONFIRMED','REVERSED') NOT NULL DEFAULT 'RESERVED',
    billing_date  DATE NOT NULL,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_tenant_date (tenant_id, billing_date),
    KEY idx_task_ref (task_ref_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-8.1 预扣/确认/冲正流水';

CREATE TABLE statements (
    id                BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    tenant_id         BIGINT UNSIGNED NOT NULL,
    period_start      DATE NOT NULL,
    period_end        DATE NOT NULL,
    send_count        BIGINT NOT NULL DEFAULT 0,
    success_count     BIGINT NOT NULL DEFAULT 0,
    billed_count      BIGINT NOT NULL DEFAULT 0,
    amount_due        BIGINT NOT NULL DEFAULT 0,
    reconcile_status  ENUM('PENDING','CONFIRMED','DISPUTED') NOT NULL DEFAULT 'PENDING',
    dispute_note      VARCHAR(500),
    settlement_status ENUM('NOT_SETTLED','PENDING_SETTLEMENT','SETTLED','PAID') NOT NULL DEFAULT 'NOT_SETTLED',
    generated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at      DATETIME NULL,
    KEY idx_tenant_period (tenant_id, period_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-8.5/F-8.6 对账单/结算单';

CREATE TABLE invoices (
    id            BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    tenant_id     BIGINT UNSIGNED NOT NULL,
    amount        BIGINT NOT NULL,
    invoice_type  VARCHAR(32),
    status        ENUM('PENDING','ISSUED') NOT NULL DEFAULT 'PENDING',
    invoice_no    VARCHAR(64),
    issued_at     DATETIME NULL,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-8.7 发票';

CREATE TABLE billing_alert_configs (
    id             BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    tenant_id      BIGINT UNSIGNED NOT NULL,
    alert_type     ENUM('PREPAID_LOW_BALANCE','POSTPAID_CREDIT_NEAR_LIMIT') NOT NULL,
    threshold_value DECIMAL(10,4) NOT NULL COMMENT '金额或百分比，视 alert_type 而定',
    notify_targets JSON,
    last_triggered_at DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-8.10 [新增] 费用预警配置';

-- ----------------------------------------------------------------------------
-- 10.9 投诉与处置数据项 (F-9)
-- ----------------------------------------------------------------------------
CREATE TABLE complaints (
    id              BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    source          ENUM('REGULATOR','OPERATOR','USER_REPORT') NOT NULL,
    tenant_id       BIGINT UNSIGNED NULL,
    signature_id    BIGINT UNSIGNED NULL,
    template_id     BIGINT UNSIGNED NULL,
    message_id      VARCHAR(64) NULL,
    channel_id      BIGINT UNSIGNED NULL COMMENT '[新增] 用于 F-11.9 通道投诉占比统计',
    summary         VARCHAR(500),
    status          ENUM('PENDING','PROCESSING','PROCESSED','CLOSED') NOT NULL DEFAULT 'PENDING',
    handling_note   VARCHAR(500),
    corrective_action VARCHAR(500),
    handled_by      VARCHAR(64),
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_tenant (tenant_id),
    KEY idx_channel (channel_id),
    KEY idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-9.1 投诉工单';

CREATE TABLE disposal_records (
    id             BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    complaint_id   BIGINT UNSIGNED NOT NULL,
    disposal_type  ENUM('BLACKLIST_MOBILE','SUSPEND_TENANT','SUSPEND_SIGNATURE_OR_TEMPLATE','SUSPEND_CHANNEL') NOT NULL,
    target_ref     VARCHAR(64) NOT NULL,
    disposed_by    VARCHAR(64),
    disposed_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resumed_at     DATETIME NULL,
    resume_condition VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-9.3 投诉联动处置记录';

CREATE TABLE tenant_alert_rules (
    id             BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    rule_name      VARCHAR(100) NOT NULL,
    metric         ENUM('COMPLAINT_RATE','FAILURE_RATE','UNSUBSCRIBE_RATE') NOT NULL,
    threshold_value DECIMAL(6,4) NOT NULL,
    duration_minutes INT NOT NULL DEFAULT 60,
    action         ENUM('NOTIFY','AUTO_SUSPEND') NOT NULL DEFAULT 'NOTIFY',
    notify_targets JSON
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-9.5 [新增] 机构级预警规则';

-- ----------------------------------------------------------------------------
-- 10.10 工具类数据项 (F-13)
-- ----------------------------------------------------------------------------
CREATE TABLE short_links (
    id            BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    tenant_id     BIGINT UNSIGNED NOT NULL,
    target_url    VARCHAR(2000) NOT NULL,
    custom_domain VARCHAR(128),
    short_code    VARCHAR(16) NOT NULL,
    valid_until   DATE NOT NULL,
    status        ENUM('PENDING','APPROVED','REJECTED','EXPIRED','TAKEN_DOWN') NOT NULL DEFAULT 'PENDING',
    click_count   BIGINT NOT NULL DEFAULT 0,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_short_code (short_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-13.1 短链';

CREATE TABLE short_link_audits (
    id                BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    short_link_id     BIGINT UNSIGNED NOT NULL,
    auto_check_result JSON COMMENT '域名黑名单比对/恶意特征/备案信息',
    risk_level        ENUM('LOW','MEDIUM','HIGH') NOT NULL DEFAULT 'LOW',
    reviewer          VARCHAR(64),
    review_comment    VARCHAR(255),
    reviewed_at       DATETIME NULL,
    last_recheck_at   DATETIME NULL,
    KEY idx_short_link (short_link_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-13.2 [新增] 短链审核记录';

CREATE TABLE status_code_mappings (
    id            BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    channel_code  VARCHAR(32) NOT NULL,
    platform_code VARCHAR(32) NOT NULL,
    meaning       VARCHAR(255),
    suggestion    VARCHAR(255),
    UNIQUE KEY uk_channel_code (channel_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-13.3 状态码映射';

-- ----------------------------------------------------------------------------
-- 10.11 告警、日志与统计数据项 (F-11, F-12, F-14)
-- ----------------------------------------------------------------------------
CREATE TABLE alert_rules (
    id               BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    rule_name        VARCHAR(128) NOT NULL,
    rule_type        VARCHAR(32) NOT NULL COMMENT '通道异常/失败率/余额不足/队列积压/投诉占比超阈值',
    metric_name      VARCHAR(64) NOT NULL,
    threshold_value  DECIMAL(12,4) NOT NULL,
    comparison_op    VARCHAR(8) NOT NULL DEFAULT '>=',
    duration_minutes INT NOT NULL DEFAULT 5,
    severity         ENUM('LOW','MEDIUM','HIGH','CRITICAL') NOT NULL DEFAULT 'MEDIUM',
    notify_channels  JSON,
    status           ENUM('ACTIVE','DISABLED') NOT NULL DEFAULT 'ACTIVE'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-12.1 告警规则';

CREATE TABLE alert_records (
    id             BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    rule_id        BIGINT UNSIGNED NOT NULL,
    title          VARCHAR(255) NOT NULL,
    content        VARCHAR(1000),
    metric_value   DECIMAL(12,4),
    status         ENUM('ACTIVE','ACKNOWLEDGED','RESOLVED') NOT NULL DEFAULT 'ACTIVE',
    triggered_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acknowledged_at DATETIME NULL,
    resolved_at    DATETIME NULL,
    KEY idx_rule (rule_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-12.3 告警记录';

CREATE TABLE operation_logs (
    id               BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    user_id          BIGINT UNSIGNED NULL,
    username         VARCHAR(50),
    user_type        VARCHAR(20),
    tenant_id        BIGINT UNSIGNED NULL,
    operation_type   VARCHAR(32) NOT NULL,
    operation_desc   VARCHAR(255),
    resource_type    VARCHAR(32),
    resource_id      VARCHAR(64),
    request_method   VARCHAR(8),
    request_url      VARCHAR(255),
    response_status  INT,
    client_ip        VARCHAR(45),
    operation_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    execution_time_ms INT,
    KEY idx_user (user_id),
    KEY idx_tenant (tenant_id),
    KEY idx_time (operation_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-14.1 操作日志';

CREATE TABLE sms_send_stats (
    id            BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    stat_date     DATE NOT NULL,
    stat_hour     TINYINT NOT NULL DEFAULT 0,
    tenant_id     BIGINT UNSIGNED NOT NULL,
    channel_id    BIGINT UNSIGNED NULL,
    operator      ENUM('MOBILE','UNICOM','TELECOM') NULL,
    message_type  ENUM('VERIFY','NOTIFY','MARKETING') NULL,
    province      VARCHAR(32),
    send_count    INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    fail_count    INT NOT NULL DEFAULT 0,
    total_cost    DECIMAL(14,4) NOT NULL DEFAULT 0,
    avg_response_ms INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_dimension (stat_date, stat_hour, tenant_id, channel_id, operator, message_type, province),
    KEY idx_date_tenant (stat_date, tenant_id),
    KEY idx_channel_date (channel_id, stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-11.1~F-11.4 统计聚合表';

CREATE TABLE complaint_ratio_stats (
    id              BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    stat_month      CHAR(7) NOT NULL COMMENT 'YYYY-MM',
    dimension_type  ENUM('CHANNEL','TENANT') NOT NULL,
    dimension_id    BIGINT UNSIGNED NOT NULL,
    send_count      BIGINT NOT NULL DEFAULT 0,
    complaint_count BIGINT NOT NULL DEFAULT 0,
    ratio           DECIMAL(8,6) NOT NULL DEFAULT 0 COMMENT 'complaint_count / send_count',
    over_threshold  TINYINT(1) NOT NULL DEFAULT 0,
    threshold_config_version VARCHAR(32),
    calculated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_month_dim (stat_month, dimension_type, dimension_id),
    KEY idx_over_threshold (stat_month, dimension_type, over_threshold)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-11.9 [新增] 投诉占比统计视图，驱动通道/机构月度投诉占比看板';

SET FOREIGN_KEY_CHECKS = 1;
