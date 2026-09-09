# Decisions

## DR-04-001: 以平台通道为界限先打通 Bootstrap

### Status
Accepted

### Context

租户路由和告警规则尚未可用，将平台级通知 bootstrap 先跑通可降低依赖深度。

### Decision

本 Phase 只实现平台专属引导通知通路，不同时推进通用告警/模板和路由模块。

### Consequences

- 提升首发速度；后续 Alert/Channel 阶段可按 SPI 替换适配器。
- 阻断跨模块耦合，降低最初实现风险。

### References

- ROADMAP phase 4 scope

## DR-04-002: 保留一个最小计划层数

### Status
Accepted

### Context

前几阶段存在过多 plan 与过重验收路径。

### Decision

Phase 4 使用 2 个计划文件：一个实现、一个测试与审计联动；不额外拆成大量微 plan。

### Consequences

- 计划执行更快，审计面更小。
- 需确保每个计划的行为链条仍完整覆盖 3 个 owned obligation。

### References

- `SKILL` 内部过度工程复盘结论

