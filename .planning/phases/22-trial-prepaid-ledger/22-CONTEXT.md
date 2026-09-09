---
phase: 22
package: trial-prepaid-ledger
status: implemented-pending-verification
---

# Phase 22 Context

本阶段只处理试用额度、预付费冻结/确认/冲正、消费账本和余额审计。范围来自 `.planning/PRD-OBLIGATIONS.md` 中 owner 为 `trial-prepaid-ledger` 的 15 条 obligation。

## Scope boundaries

- 包含：后端账本表、试用状态服务、预付费冻结/确认/冲正、平台试用配置页面、机构试用状态/转正入口、机构消费账本、平台余额审计。
- 不包含：低余额预警、授信额度、异步导出、正式合同审批、全量报表统计。
- 浏览器验证只使用本机 Chrome 的 `local-google-chrome` project。
