# PRD 全链路复核（新增问题）

本记录不是对 Issue #89–#92 的重复，而是把 PRD 的需求、Web 页面、Controller、Service、数据库状态和异步动作串联后的新增审查结果。

## 复核结论

当前代码具备大量真实页面和单元测试，但“短信受理 → 出队 → 上游发送 → 回执 → 计费确认”的生产闭环尚未自动运行；若只通过 HTTP 接口提交，消息会进入待发送/READY 数据状态，却没有应用内调度器持续调用派发服务。

## 新增 P0：受理消息没有自动派发

- `MessageController POST /api/v1/sms/send` 调用 `MessageSubmitService.submit()`，写入 `message_tasks`、计费预扣和 `message_send_outbox`。
- `HttpMessageDeliveryService.dispatchNext()` 只提供 Java 方法，没有 `@Scheduled`、队列消费者或其它运行时调用方。
- 全项目唯一 `@Scheduled` 是投诉占比统计；`MessageDeliveryController` 只接收回执和查询状态，不触发派发。

因此真实发送流程停在 READY outbox，只有测试直接调用 service 才能发送。应新增受控的 dispatcher worker（批量 claim、并发上限、失败退避、优雅停机），并用集成测试证明：发送 API 返回后，outbox 自动变为 SENT/FAILED，随后回执能完成 CONFIRMED/REVERSED 计费闭环。

## 新增 P0：计费金额没有使用通道/合同价格

`MessageSubmitService` 将预扣金额固定为 `0.05` 元，并明确注释“简化为固定演示单价”；PRD F-4.1、F-8.1、F-8.8 要求按通道成本、机构价格、计费模式和阶梯价计费。路由选出的 `channelId` 没有参与单价计算，试用额度也没有在提交链路调用 `tryConsumeTrialQuota()`。

应把“价格解析”作为受理前的单一服务：先读取机构合同/试用状态，再按产品、通道、阶梯价计算预扣金额；账单记录保存实际单价、价格版本和计费来源。增加预付费、后付费、试用额度、余额不足和回执冲正的端到端测试。

## 新增 P1：默认路由不满足最低价/条件匹配语义

`ChannelSelector` 在没有命中规则时按 `Channel.priority` 最大值选择通道，而不是按短信类型和价格选择；验证码“最低价优先”的业务规则没有实现。`matches()` 只实际判断 operator，号段、内容关键词、时间段等 PRD F-5.8 条件没有参与匹配，注释还说明明文号段匹配留给后续。

应明确路由决策输入（消息类型、运营商/号段、机构、关键词、时间窗、通道健康、价格），按规则优先级筛选候选后再执行验证码最低价或营销流量策略；在决策证据中记录命中的规则、候选通道、最终通道和价格。

## 新增 P0：PRD 要求的外部批量 HTTP API 缺失

PRD F-6.2 要求下游 HTTP API 一次请求提交多个号码并返回逐条跟踪 ID。当前 `/api/v1/sms` 只有 `POST /send` 单条入口；批量能力位于 `/api/v1/console/tenant/bulk/*`，依赖控制台租户会话，不是外部 HMAC API，也没有逐条受理结果契约。

应增加独立批量 API（HMAC、幂等 batch key、逐条校验/状态、部分失败语义、限流），并补充 OpenAPI/Playwright 或 API 集成验收；不能用控制台批任务接口冒充外部 API。

## 新增 P1：文档对“已交付”描述超出实际边界

`web/docs/ROADMAP.md` 将“通道配置与健康、试用/充值、告警、短链”等列为已交付，但代码中仍有 `PlaceholderPage`（租户配置 F-2.6/F-5.2/F-6.6），核心包说明也标注 F-12、F-13 等独立服务尚未实现。建议文档按“页面存在 / API 已实现 / 真实外部联调”三种状态拆分，避免把可点击页面或 Sandbox 数据写成完整交付。

## 建议执行顺序

1. 先补 dispatcher worker 和发送—回执—计费集成验证，恢复核心业务闭环。
2. 再补价格/试用/后付费统一计费决策和路由条件匹配。
3. 增加真正的外部批量 HTTP API。
4. 最后校正 ROADMAP/使用手册的实现边界，并为尚未实现的 F-2.6 等功能单独建需求。
