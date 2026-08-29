# core 实现进度与路线图

本文档如实记录 core 后端相对于 PRD（`docs/PRD.md`，即 `ycsansms.md` v1.3.0）的实现进度。
**目的是让下一个接手的人（人或 agent）不用重新读一遍全部代码就知道从哪里继续**，
所以宁可条目多、状态诚实，也不写"大体完成"这种无法验证的说法。

## 已实现并有测试覆盖

| 模块 | PRD 编号 | 实现位置 | 测试 |
|---|---|---|---|
| MySQL 完整 schema | 第 10 章全部实体 | `src/main/resources/db/migration/V1__init_schema.sql` | Flyway 启动时执行校验；未做单独的 schema 测试 |
| 路由引擎：黑名单检测 | F-5.1/F-5.2 | `service/routing/BlacklistChecker.java` | `RoutingEngineTest`（编排层）；`BlacklistChecker` 自身规则的单测待补 |
| 路由引擎：内容审核 | F-5.5 | `service/routing/ContentReviewChecker.java` | `RoutingEngineTest`（编排层）；自身单测待补 |
| 路由引擎：频次拦截 | F-5.6 | `service/routing/FrequencyChecker.java`（Redis 计数） | `RoutingEngineTest`（编排层）；需要 Redis 环境才能跑真实集成测试，当前无 |
| 路由引擎：通道选择 | F-5.8/F-5.9（简化版，见下方"已知简化"） | `service/routing/ChannelSelector.java` | `RoutingEngineTest`（编排层） |
| 路由引擎编排 | F-5 全流程"任一命中即拒绝" | `service/routing/RoutingEngine.java` | `RoutingEngineTest`（5 个场景，全绿） |
| 第三方黑名单超时降级 | F-5.3 | `service/routing/ThirdPartyBlacklistClient.java` | 无单测；**真实请求体/响应解析未实现，见下方 TODO** |
| 预付费计费：预扣/确认/冲正 | F-8.1 | `service/billing/BillingService.java`（乐观锁并发安全） | `BillingServiceTest`（5 个场景，全绿） |
| HTTP API 签名机制 | F-6.4（9.1 节） | `common/security/HmacSignatureVerifier.java` | `HmacSignatureVerifierTest`（5 个场景，全绿） |
| 字段级加密 | 6.2.1 节 | `common/security/FieldEncryptor.java`（AES-256-GCM） | 无单测（TODO） |
| 机构注册/审核/试用激活 | F-2.1/F-2.2/F-2.8 | `service/tenant/TenantService.java` | 无单测（TODO） |
| 消息提交编排 | F-6.1（含 F-3.7 前置校验） | `service/message/MessageSubmitService.java` | 无单测（TODO，建议参考 `RoutingEngineTest` 的 mock 风格） |
| 投诉占比统计 + 仪表盘接口 | **F-11.9（本次新增需求）** | `service/complaint/ComplaintRatioService.java` + `web/controller/DashboardController.java` | `ComplaintRatioServiceTest`（3 个场景，全绿） |
| 通道暂停/恢复 | F-4.7 | `web/controller/ChannelController.java` | 无单测（TODO） |
| 控制台登录 | F-1.4（bcrypt，非 MD5） | `service/account/AuthService.java` | 无单测（TODO） |

全部以上代码在 2026-08-29 于 Java 21 + Maven 3.9 环境下 `mvn compile` 与 `mvn test`
均通过（18/18 测试通过），不是未经验证的草稿。

## 已知简化（能跑，但不是生产完整实现）

1. **CMPP 协议完全未实现**（F-6.7/F-6.8/F-6.9）——`cmpp/` 包目前只有 `package-info.java`。
   这是整个仓库里最大的缺口，涉及长连接管理、窗口控制、心跳、PDU 编解码，工作量相当于
   独立的一个子系统，不适合用一个"能跑但是假的" socket 实现来冒充。
2. **上游通道真实投递未实现**——`MessageSubmitService` 选出通道 ID 后没有真的把消息发给
   上游（无论 CMPP 还是 HTTP 通道商）。当前只落库 `message_tasks` 记录，状态永远停在 PENDING。
3. **HMAC 签名校验不完整**——`HmacAuthInterceptor` 校验了 App Key 存在性、时间戳、nonce，
   但**没有真正比对签名**（需要 `ContentCachingRequestWrapper` 读取 body 再拼接签名串，
   与 `TenantApiKey.appSecretEncrypted` 解密后比对）。生产上线前必须补全，否则鉴权形同虚设。
4. **nonce 防重放用内存 `Set`**——多实例部署下完全失效，必须换成 Redis + TTL。
5. **通道选择是"最高优先级可用通道"，没有真正的权重轮询/失败率熔断统计**（F-5.9）。
6. **频次规则的 CONTENT_SIMILARITY 维度未实现**（需要内容指纹算法）。
7. **手机号在多处仍以明文形式经过方法参数**（如 `MessageTask.mobileEncrypted` 字段实际存的是
   `request.phoneNumber()` 明文，未调用 `FieldEncryptor.encrypt`）——这是为了先跑通业务流程
   刻意留的技术债，**上线前必须补齐，否则违反 PRD 6.2.1 节的加密存储硬性要求**。
8. **RoutingEngine 依赖的号码归属识别（F-5.7）尚未接入**——`RoutingContext.operatorHint`
   目前始终为 null，`ChannelSelector`/`FrequencyChecker` 里依赖它的分支实际不会命中。

## 完全未实现（PRD 中存在但本次 scaffold 未触碰）

- F-3.3 签名通道报备流程
- F-3.6 免审规则的实际生效逻辑（表已建，服务未写）
- F-4.2/F-4.3/F-4.4/F-4.6 通道热更新、健康检查、删除迁移、通道池
- F-5.10 重发规则的实际执行（表已建）
- F-6.2/F-6.3/F-6.5/F-6.6/F-6.10/F-6.11/F-6.12 批量发送、状态查询、限流中间件、Webhook 回调、
  在线发送后端支持、批量导入、定时任务
- F-7 全部详单查询接口（表已建，查询 API 未写）
- F-8.2~F-8.9 后付费、充值审核、对账、结算、发票、成本核算、余额审计、F-8.10 费用预警的
  实际告警触发逻辑
- F-9.2~F-9.5 投诉处理流转、联动处置、统计、机构级自动暂停的调度逻辑
- F-10 上行数据总览、退订关键词处理、退订统计、推送监控
- F-11.1~F-11.8、F-11.10 除投诉占比外的其余统计与仪表盘配置
- F-12 告警规则引擎与多渠道通知
- F-13 短链生成/审核、状态码管理、号段管理、发送任务管理
- F-14 操作日志的实际埋点（表已建，AOP 切面未写）
- 第 12 章：签约管理（F-2.9）、终止管理（F-2.10）——`Tenant` 实体已建好相应字段，Service 方法未写

## 建议的下一步顺序

1. 补全 HMAC 签名真实校验 + Redis nonce（安全底线，优先级最高）。
2. 打通手机号字段加密（合规底线）。
3. 实现一个 HTTP 上游连接器（先不做 CMPP，成本更低）打通"真实发出一条短信"的闭环。
4. 补 F-7 详单查询接口——这是验证前面所有链路是否正确的最直接方式。
5. 再按业务优先级（PRD 第 2.5 节路线图）推进其余模块。
