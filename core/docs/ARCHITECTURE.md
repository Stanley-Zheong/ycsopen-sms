# core 架构说明

摘自 PRD（`docs/PRD.md`）第 4 章，聚焦本仓库的包结构与 PRD 分层的对应关系。

## 分层架构 <-> 包结构

| PRD 分层（4.2 节） | Java 包 |
|---|---|
| 接入层 | `web.controller`、`web.interceptor` |
| 业务层 | `service.*`（按 F 模块分包：`account`/`tenant`/`signature`/`channel`/`routing`/`billing`/`complaint`/`message`/`alert`/`tool`） |
| 通道层 | `cmpp`（当前为空，见 ROADMAP.md） |
| 基础设施层 | `domain.entity` + `repository`（MySQL/JPA）、`common.security`（Redis 由 Spring Data Redis 直接注入到需要的 Service） |

## 路由引擎（F-5）内部结构

这是本仓库实现得最完整的一块，专门画出来：

```
MessageSubmitService
  └─ RoutingEngine.route(RoutingContext) -> RoutingDecision
       ├─ ① BlacklistChecker      (F-5.1/F-5.2/F-5.3)
       ├─ ② ContentReviewChecker  (F-5.5)
       ├─ ③ FrequencyChecker      (F-5.6, Redis 计数)
       └─ ④ ChannelSelector       (F-5.8/F-5.9，简化版)
```

任一检查器返回"拦截"，`RoutingEngine` 立即短路返回，不执行后续步骤——这是 F-5 系列需求
"任一命中即拦截"的代码级体现，测试见 `RoutingEngineTest`。

## 为什么手机号在多数地方是 `mobileHash` 而不是明文

PRD 6.2.1 节要求手机号等个人敏感信息字段级加密存储。为了让"能等值查询"与"不可逆推明文"
同时成立，采用了"密文列 + 确定性哈希列"两列并存的设计（`HashUtil.sha256Hex`）：

- 业务逻辑（路由、频控、黑名单匹配）全程只用 `mobileHash` 做等值比较，从不解密。
- 只有真正需要把号码交给上游通道/短信网关时，才在最后一步用 `FieldEncryptor.decrypt` 拿明文，
  且不应该把明文继续传递或落日志。

当前实现里 `MessageSubmitService`/`MessageTask.mobileEncrypted` 还留了明文技术债
（见 ROADMAP.md 已知简化第 7 条），这是文档里主动标注、留给后续会话处理的缺口。

## 依赖注入关系图（服务层）

```
MessageSubmitService
  ├─ TemplateRepository / SignatureRepository   (F-3.7 前置校验)
  ├─ RoutingEngine                              (F-5)
  ├─ BillingService                             (F-8.1)
  └─ MessageTaskRepository                      (落库)

ComplaintRatioService
  ├─ TenantRepository / ChannelRepository       (维度枚举)
  ├─ MessageTaskRepository                      (发送量分母)
  ├─ ComplaintRepository                        (投诉量分子)
  └─ ComplaintRatioStatsRepository              (结果落库，供 DashboardController 查询)
```
