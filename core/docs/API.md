# core API 一览

统一响应结构（PRD 9.1 节）：`{code, message, data, timestamp, traceId}`。

## 下游开放 API（HMAC 签名鉴权，见 HmacAuthInterceptor —— 校验尚不完整，见 ROADMAP.md）

| 方法 | 路径 | 说明 | PRD 编号 |
|---|---|---|---|
| POST | `/api/v1/sms/send` | 单条发送 | F-6.1 |

**待实现**：`/api/v1/sms/batch-send`（F-6.2）、`/api/v1/sms/query`（F-6.3）。

## 控制台 API（当前未接 JWT 鉴权拦截器，见 core/docs/ROADMAP.md 与 SecurityConfig 的 TODO）

| 方法 | 路径 | 说明 | PRD 编号 |
|---|---|---|---|
| POST | `/api/v1/console/auth/login` | 登录 | F-1.4 |
| POST | `/api/v1/console/tenants/register` | 机构注册 | F-2.1 |
| POST | `/api/v1/console/tenants/{id}/approve-and-activate-trial` | 审核通过并开通试用 | F-2.2/F-2.8 |
| POST | `/api/v1/console/tenants/{id}/reject` | 驳回注册 | F-2.2 |
| GET | `/api/v1/console/channels` | 通道列表 | F-4.1 |
| POST | `/api/v1/console/channels` | 新建通道 | F-4.1 |
| POST | `/api/v1/console/channels/{id}/pause` | 暂停通道 | F-4.7 |
| POST | `/api/v1/console/channels/{id}/resume` | 恢复通道 | F-4.7 |
| GET | `/api/v1/console/dashboard/complaint-ratio/channel` | **通道投诉占比排行（本次新增需求）** | F-11.9 |
| GET | `/api/v1/console/dashboard/complaint-ratio/tenant` | **机构投诉占比排行（本次新增需求）** | F-11.9 |

其余控制台 API（详单查询、审核中心、财务、告警、工具管理等）均未实现，见 ROADMAP.md。

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
