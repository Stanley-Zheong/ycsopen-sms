# core API 一览

统一响应结构（PRD 9.1 节）：`{code, message, data, timestamp, traceId}`。

## 下游开放 API（HMAC 签名鉴权，见 HmacAuthInterceptor —— 校验尚不完整，见 ROADMAP.md）

| 方法 | 路径 | 说明 | PRD 编号 |
|---|---|---|---|
| POST | `/api/v1/sms/send` | 单条发送 | F-6.1 |

**待实现**：`/api/v1/sms/batch-send`（F-6.2）、`/api/v1/sms/query`（F-6.3）。

## 控制台 API（JWT 会话与当前数据库 RBAC）

| 方法 | 路径 | 说明 | PRD 编号 |
|---|---|---|---|
| POST | `/api/v1/console/auth/login` | 登录 | F-1.4 |
| POST | `/api/v1/console/session/logout` | 撤销当前会话 | F-1.4 |
| GET | `/api/v1/console/account-overview` | 当前账号、角色与权限 | F-1.4 |
| GET/POST/PUT | `/api/v1/console/platform-accounts` | 平台账号查询、新建与修改 | F-1.1 |
| POST | `/api/v1/console/platform-accounts/{id}/{disable,enable,unlock}` | 账号状态变更 | F-1.1/F-1.3 |
| POST | `/api/v1/console/platform-accounts/{id}/phone/reveal` | 有用途、无缓存、留审计的临时手机号查看 | 6.2.1 |
| GET/POST/PUT | `/api/v1/console/platform-roles` | 平台角色查询、新建与修改 | F-1.2 |
| PUT | `/api/v1/console/platform-roles/{id}/permissions` | 原子替换角色权限并记录变更 | F-1.2 |
| GET | `/api/v1/console/login-history` | 按当前权限范围查询登录历史 | F-1.3 |
| GET | `/api/v1/console/operation-audits` | 按操作人、操作、结果、时间查询脱敏操作日志 | F-14.1 |
| GET | `/api/v1/console/security-events` | 按事件、操作人、结果、时间查询安全事件 | F-14.2 |
| GET | `/api/v1/console/system-configuration` | 查询类型化配置、当前版本、草稿与历史；敏感引用固定脱敏 | 8.1 System |
| POST | `/api/v1/console/system-configuration/versions` | 按当前版本和变更原因暂存配置变更 | 8.1 System |
| POST | `/api/v1/console/system-configuration/versions/{id}/activate` | 以乐观并发检查激活草稿并热加载 | 8.1 System |
| POST | `/api/v1/console/system-configuration/versions/{id}/rollback` | 从历史快照创建并激活新版本 | 8.1 System |
| POST | `/api/v1/console/tenants/register` | 旧机构注册路径，仅返回迁移提示；请使用 public tenant registration | 兼容 |
| POST | `/api/v1/console/tenants/{id}/approve-and-activate-trial` | 旧审核路径，仅返回迁移提示；请使用 admin tenant decision | 兼容 |
| POST | `/api/v1/console/tenants/{id}/reject` | 旧驳回路径，仅返回迁移提示；请使用 admin tenant decision | 兼容 |
| GET | `/api/v1/console/channels` | 通道列表 | F-4.1 |
| POST | `/api/v1/console/channels` | 新建通道 | F-4.1 |
| POST | `/api/v1/console/channels/{id}/pause` | 暂停通道 | F-4.7 |
| POST | `/api/v1/console/channels/{id}/resume` | 恢复通道 | F-4.7 |
| GET/POST | `/api/v1/console/complaints` | 查询或登记投诉工单，保留可用归因和显式未知状态 | F-9.1 |
| GET | `/api/v1/console/complaints/{id}` | 查询单个投诉快照、完整事件时间线与该案件全部处置记录 | F-9.2/F-9.3 |
| GET | `/api/v1/console/complaint-reference-options` | 查询登记投诉所需的机构、通道、签名和模板最小选项；仅 ADMIN/OPERATOR 可用 | F-9.1 |
| POST | `/api/v1/console/complaints/{id}/{accept,handle,close}` | 按状态机受理、处理或关闭投诉并记录服务端操作人证据 | F-9.2 |
| POST | `/api/v1/console/complaints/{id}/remediations` | 对投诉归因的确切号码、机构、签名、模板或通道执行幂等处置 | F-9.3 |
| GET | `/api/v1/console/complaint-remediations` | 查询最近 200 个可见投诉各自的最新处置与最新失败状态，供补偿入口读回 | F-9.3 |
| POST | `/api/v1/console/complaints/{id}/recoveries` | 对失败处置记录授权复核与人工补偿证据 | F-9.3 |
| GET | `/api/v1/console/complaint-analytics` | 查询按创建日期的投诉趋势、归因质量及机构/签名/内容类型分布 | F-9.4 |
| GET | `/api/v1/console/dashboard/complaint-ratio/channel` | **通道投诉占比排行（本次新增需求）** | F-11.9 |
| GET | `/api/v1/console/dashboard/complaint-ratio/tenant` | **机构投诉占比排行（本次新增需求）** | F-11.9 |
| GET | `/api/v1/console/uplinks` | 按稳定 `tenantId`、号码、关键词、运营商、推送状态和时间查询上行记录 | F-7.5/F-10.1 |
| GET | `/api/v1/console/uplinks/{id}` | 查询一条上行详情 | F-7.5 |
| GET | `/api/v1/console/uplinks/push-monitor` | 按稳定 `tenantId`、状态和目的地查询上行推送证据 | F-10.4 |
| GET | `/api/v1/console/uplinks/tenant-options` | ADMIN/OPERATOR 按机构编号、简称或全称查询最多 20 个安全机构选项 | F-10.1/F-10.4 |

上行记录、详情和推送监控响应在原有 `tenantId` 外，附带 `tenantNo`、
`tenantShortName` 和 `tenantFullName`。这些字段由服务端在列表查询中一次投影，不要求客户端逐行查询。
历史上行对应的机构资料不存在或不可用时，三个展示字段可以为 `null`，`tenantId` 仍然保留。
`tenant-options` 只返回上述四个机构身份字段；`query` 最多取前 100 个字符参与包含匹配，响应按简称、
机构编号和内部 ID 排序并限制为 20 条。上行列表和推送监控的正式筛选参数始终是 `tenantId`，名称和编号
只用于选择该稳定 ID。

投诉详情 `data` 固定为 `{complaint, timeline, remediations}`。`timeline` 按
`occurredAt ASC, id ASC` 排序，每项包含 `id`、`complaintId`、`eventType`、`actor`、
`occurredAt`、`fromStatus`、`toStatus`、`evidenceText`、`targetRef`、`result`、
`reviewId`、`failureReason` 和 `relatedDisposalId`。旧数据只回填仍可由案件或处置快照证明的事实；
已被后续动作覆盖的历史意见或复核编号保持 `null`，不得解释为完整证据。`remediations` 按
`disposedAt DESC, id DESC` 投影，其中首条 `status=FAILED` 的记录是恢复动作重新校验的最新失败记录。

投诉引用选项 `data` 固定为 `{tenants, channels, signatures, templates}`，每项仅返回
`{id, label, tenantId}`，每类最多 500 条。机构和通道的 `tenantId` 为 `null`；签名和模板返回其真实
所属机构。登记前服务端校验所有非空引用存在；选定机构时，签名和模板必须属于该机构，未选机构但同时
选择签名和模板时，二者已知的所属机构必须一致。拒绝码分别为 `COMPLAINT_REFERENCE_NOT_FOUND` 和
`COMPLAINT_REFERENCE_TENANT_MISMATCH`。服务端只根据验证通过的稳定引用推导归因质量，不接受客户端
声明的归因质量。受理意见必填；处置原因最长 255 个字符。下游处置失败只持久化固定、安全的失败说明；
V6600 回填和所有处置读模型也会将旧的非空失败字符串投影为同一安全说明。原始异常文本不会进入新案件记录、
事件时间线或 API 响应；
受理、处理、关闭、处置和恢复都由服务端校验当前状态。并发状态变化返回 HTTP 409，稳定机器码位于
`data.errorCode`，其中刷新后重试使用 `COMPLAINT_STATE_STALE`。FINANCE 可读取案件、详情、时间线和
处置记录，但不能读取登记选项或调用变更接口。

其余控制台 API（详单查询、审核中心、财务、告警、工具管理等）的真实边界见 ROADMAP.md。

操作日志的结果集合为 `STARTED`、`SUCCESS`、`CLIENT_FAILURE`、`SERVER_FAILURE`、`DENIED`。
`STARTED` 表示请求已在进入控制器前持久化，但终态写入中断，需人工调查。安全事件类型限定为
`UNUSUAL_LOGIN`、`REPEATED_LOGIN_FAILURE`、`BULK_EXPORT`，结果限定为 `DETECTED`、`BLOCKED`、
`SUCCESS`、`FAILURE`。未知筛选值返回 HTTP 400。

系统配置权限分为 `system:configuration:menu`、`system:configuration:read`、
`system:configuration:write` 和 `system:configuration:activate`。服务端只接受登记的类型化 key 和
非空变更原因；客户端只提交变化字段，不能回传脱敏占位值。版本冲突、不可激活状态与热加载拒绝
返回 HTTP 409，且不会静默改变当前运行时配置。配置 mutation 的稳定机器码位于
`data.errorCode`（例如 `STALE_VERSION`、`RELOAD_REJECTED`）；`message` 是可翻译展示文本，客户端
不得据此判断错误类型。查询只返回最新 50 条不可变版本记录，这是当前管理页的明确边界。

## 机构注册证明材料暂存合同

机构注册证明材料使用私有、分阶段的受保护对象合同。客户端先创建会话，再以同一个会话凭据按用途
逐个上传，最后把会话 ID 与五个用途绑定的 `pobj_v1_*` 对象 ID 交给注册 JSON。接口不会接收或返回
对象存储 URL、bucket、key、public reference 或 presigned reference，也不会远程抓取旧客户端地址。

| 方法 | 路径 | 请求 | 成功响应 `data` |
|---|---|---|---|
| POST | `/api/v1/console/tenants/registration-object-sessions` | 无请求体 | `registrationObjectSessionId`、一次返回的 `regup_v1_` 会话凭据、`expiresAt` |
| POST | `/api/v1/console/tenants/registration-object-sessions/{sessionId}/objects/{purpose}` | `X-Registration-Upload-Token`；`multipart/form-data` 且只能有一个名为 `file` 的文件 part | `protectedObjectId`、`purpose`、`expiresAt` |
| DELETE | `/api/v1/console/tenants/registration-object-sessions/{sessionId}` | `X-Registration-Upload-Token` | 终态 `CLOSED` |

运行时用途、媒体和容量常量如下；明文与完整 YCSE/v1 信封都在进入加密或私有存储前执行有界校验，
且服务端签名字节必须与声明媒体类型一致。

| `{purpose}` | 必填性 | 媒体类型 | 最大明文字节 | 最大完整信封字节 |
|---|---|---|---:|---:|
| `business-license` | 必填 | PDF/JPEG/PNG | 10,485,760 | 10,485,905 |
| `legal-rep-id-front` | 必填 | JPEG/PNG | 5,242,880 | 5,243,025 |
| `legal-rep-id-back` | 必填 | JPEG/PNG | 5,242,880 | 5,243,025 |
| `shortlink-domain-proof` | 可选 | PDF/JPEG/PNG | 10,485,760 | 10,485,905 |
| `trademark-proof` | 可选 | PDF/JPEG/PNG | 10,485,760 | 10,485,905 |

会话 TTL 固定为 `PT24H`。状态机为 `OPEN -> CLAIMED | CLOSED | EXPIRED`；只有 `OPEN` 可上传。
同一个 `regup_v1_` 凭据可顺序上传五种用途，也可在同一会话仍为 `OPEN` 时替换该用途当前的
`STAGED` 对象。成功 claim、显式 close 或 expiry 都使凭据失效。凭据由一个非秘密 lookup ID 和
32 字节 CSPRNG secret 构成；服务端只保存 `REGISTRATION_UPLOAD` 域、会话/tenant-draft 绑定的
ACTIVE 版本 32 字节摘要。验证只接受存储的 ACTIVE/RETIRING 版本并使用常量时间比较；未知、
RETIRED、REVOKED、跨 session、跨 tenant-draft 或 `OBJECT_CAPABILITY` 域复用一律失败关闭。

媒体、大小与 magic 检查通过后，服务会在任何加密/存储工作之前原子预留一次 admitted attempt。
每个 purpose 最多 3 次，每个 session 最多 15 次；并发调用不能越界。预留后的加密、provider、
store、元数据或 reconciliation 失败会烧掉该 slot，不回退计数；越界固定返回 HTTP 429
`REGISTRATION_UPLOAD_LIMIT_REACHED`。替换以 operation ID 收敛旧对象，响应和错误均不暴露凭据、
存储定位或 provider 细节。

注册 JSON 的固定对象字段为 `businessLicenseObjectId`、`legalRepIdFrontObjectId`、
`legalRepIdBackObjectId`、`shortlinkDomainProofObjectId`、`trademarkProofObjectId`，并携带
`registrationObjectSessionId`。旧 `*Url` 字段或任何 HTTP(S) 形态的值固定返回 HTTP 422
`LEGACY_OBJECT_URL_NOT_ACCEPTED`；缺少必填对象使用 `REGISTRATION_OBJECT_REQUIRED`。

稳定错误还包括：`REGISTRATION_UPLOAD_INPUT_INVALID`、`REGISTRATION_UPLOAD_TOKEN_INVALID`、
`REGISTRATION_UPLOAD_SESSION_NOT_OPEN`、`REGISTRATION_UPLOAD_SESSION_EXPIRED`、
`REGISTRATION_UPLOAD_MEDIA_TYPE_NOT_ACCEPTED`、`REGISTRATION_UPLOAD_SIZE_LIMIT_EXCEEDED`、
`REGISTRATION_UPLOAD_SIGNATURE_MISMATCH`、`REGISTRATION_UPLOAD_LIMIT_REACHED` 和
`REGISTRATION_UPLOAD_UNAVAILABLE`。错误正文不回显会话凭据、对象存储定位或底层 provider 信息。
