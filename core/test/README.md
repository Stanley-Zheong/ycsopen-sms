# core/test

顶层测试资产目录（区别于 Maven 约定的 `src/test/java` 单元测试）：

- `integration/` —— 预留给需要真实 MySQL/Redis 的集成测试（如 Testcontainers），当前为空。
- `load/` —— 预留给压测脚本（对应 PRD 2.3 节"同步发送峰值 ≥1000 条/秒"的验收），
  建议用 k6 或 Gatling，当前为空。

单元测试见 `../src/test/java/`（Maven 强制约定，不能移到这里）。
