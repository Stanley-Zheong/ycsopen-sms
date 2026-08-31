# crypto-storage-bootstrap: Cryptographic storage and migration bootstrap

## Intent

建立单一、失败关闭的受保护数据边界，使当前可执行数据库写入、证据对象、密钥生命周期和存量迁移都能通过真实基础设施证据验证，且不会在应用数据、日志或证据中留下禁止内容。

## Scope

### In

- `YCSE/v1` 信封格式、行级/对象级 AAD、每值 DEK 与不透明 KEK/HMAC 密钥端口。
- Java 21 SunPKCS11 生产适配器与 SoftHSM 协议一致性验证。
- 当前可执行数据库读写面的显式保护适配器和多版本 blind-index 元数据。
- 私有、应用层加密的对象存储和到期受控的应用能力链接。
- V1200 Phase-owned 元数据、签名迁移预检、可恢复检查点、完整性验证、日志脱敏和泄漏扫描。

### Out

- 密码哈希、身份凭据模型和认证流程，由 Phase 5 负责。
- RBAC、默认掩码、特权完整值访问和访问审计，由 Phase 6 负责。
- 业务模块新增表结构、资格工作流/UI 和冷归档生命周期，由各自后续阶段负责。
- 旧 V1 DDL 修改、公开对象链接、生产测试适配器回退和浏览器/UI 验收。

## Behavior contracts

| Owned catalog target | Behavior ID | Static condition | State | Trigger | Subject | Required outcome | Errors and boundaries | Binary acceptance |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `EVIDENCE/OBL-CRYPTO-STORAGE-001.json` | `crypto-storage-bootstrap-01` | 保护清单已通过且当前 reader/writer 无未决分类 | 生产保护配置有效，目标容量满足 YCSE/v1 上限 | 当前消息或租户注册路径写入、读取或无关字段回写 | 显式 protected persistence adapter | 以 tenant/table/field/immutable-row AAD 写入二进制信封，并按 key version 单独写 blind-index 元数据；调用方不接触密钥 | HSM 不可用、上下文不匹配、容量超过 110 UTF-8 bytes、未知 writer、`REVIEW_REQUIRED` 或可执行面 deferral 均失败且不产生部分行 | 真实 MySQL 原始单元可严格解析为 YCSE、禁止 canary/原始移动号码哈希计数为零、上下文交换拒绝、清单接受模式通过 |
| `EVIDENCE/OBL-CRYPTO-STORAGE-002.json` | `crypto-storage-bootstrap-02` | 对象用途、媒体签名、大小、tenant-draft/session 和授权端口已声明 | 对象为 STAGED/CLAIMED 且私有桶拒绝匿名访问 | 上传、原子 claim 或使用应用能力读取 | `ProtectedObjectService` 与 deny-by-default authorization port | 加密完整字节后才写私有 S3，持久化 opaque object ID 和能力摘要，并只在能力与授权全部通过后完整认证再返回内容 | legacy URL、公开/直链、错误 purpose/session、过期/重用能力、校验和/tag 错误、对象成功但元数据失败均稳定拒绝或进入可重放 reconciliation | 真实 MinIO 匿名访问拒绝、原始对象无 payload canary、真实 MySQL 无 URL/token、五类注册对象完成 staged/claim/rollback/reconcile 验证 |
| `EVIDENCE/OBL-CRYPTO-STORAGE-003.json` | `crypto-storage-bootstrap-03` | 生产 profile 只配置 PKCS#11 模块/token/alias 引用 | 每个 purpose 恰有一个 ACTIVE，旧 key 在安全退休前保持 DECRYPT_ONLY/RETIRING | wrap/unwrap/HMAC、activate、rewrap、restart、retire 或 recovery | SunPKCS11 adapter 与 key lifecycle owner | KEK/HMAC key 始终为不可导出的 token handle；rewrap 只改 wrap 元数据并保持数据密文可读；应用不自动删除 token key | 缺失/重复/可导出/错误用途 key、机制缺失、provider outage、并发 activate、过早 retire 和 test adapter 生产选择均失败关闭 | 真实 SoftHSM 上 wrap/unwrap/HMAC/rotation/reopen/fault 全部通过，真实 MySQL 保持可读，代码/DB/log/evidence 无原始 key material |
| `EVIDENCE/OBL-CRYPTO-STORAGE-004.json` | `crypto-storage-bootstrap-04` | canonical inventory、writer fence 和 encrypted snapshot manifest 均已签名且 subject 一致 | 目标按 DISCOVERED→BACKFILLED→VERIFIED→CUTOVER→SCRUBBED→COMPLETE 推进 | 执行固定 `phase03-migration` preflight/start/resume/recovery 命令 | `ProtectedDataMigrationRunner` 与 migration state repository | 有界批次内对原值分类、加密、立即解密比对完整性，并让行更新、结果计数和检查点同事务提交；重启幂等 | 缺失/空/过期/重放/伪造 manifest、schema/Flyway/database 不匹配、超容量、歧义、并发变化或故障均在对应边界失败；回退只允许 forward repair 或经验证的加密快照 | 真实 MySQL 证明拒绝分支零 UPDATE/event/checkpoint、每个目标可从故障点恢复、完整性一致、raw fallback 在 COMPLETE 后不可达、V1 checksum 不变 |

## Compatibility contract

- `YCSE/v1` 为 current-write/read-supported；任何新格式使用新版本，不能重解释 v1 字节。
- 多版本 blind index 每个目标、字段和 key version 独占一行。ACTIVE 与 RETIRING 查询集合并；原始 SHA 兼容读取仅在该目标未 COMPLETE 时存在。
- V1200 只创建 `ycs.sms.crypto-storage-bootstrap.*` 元数据。V1 历史表只做已审查内容迁移，不执行 ALTER、DROP、RENAME 或隐式 shadow DDL。
- 租户证据固定采用 session → purpose-bound multipart upload → opaque ID → atomic claim；所有 legacy `*Url` 输入以稳定 422 错误拒绝。
- `core/docs/API.md` 与 `docs/使用手册.md` 必须与 DTO、端点、媒体/大小规则、生命周期和错误码保持一致。

## Requirement trace

| Requirement | Behavior IDs | Verification contract |
| --- | --- | --- |
| `REQ-NFR-DATA-PROTECTION` | `crypto-storage-bootstrap-01`, `crypto-storage-bootstrap-02`, `crypto-storage-bootstrap-03`, `crypto-storage-bootstrap-04` | `TEST-MATRIX.md` 四行、exact-four evidence manifest、真实服务结果和 blocking-free independent reviews |

## Completion rule

本阶段没有估算或百分比状态。只有当前 scope 的权威 `TODO.md` 查询为空、四个 catalog target 验证通过、独立 GSD/Claude 审查无阻断项且远端交付证明通过，阶段才完成。
