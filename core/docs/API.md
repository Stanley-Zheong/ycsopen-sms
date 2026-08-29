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
