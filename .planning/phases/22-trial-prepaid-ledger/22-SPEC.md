---
phase: 22
package: trial-prepaid-ledger
---

# Phase 22 Spec

## Delivered behavior

1. 平台可为机构启用或调整试用额度，额度必须为正整数，默认 500 条；开始时间不得晚于结束时间，默认有效期 14 天。
2. 试用发送按消息引用幂等扣减额度，额度耗尽或有效期过期会冻结新发送；历史消费记录仍可查询。
3. 机构可在试用或试用冻结状态发起转正式合作申请。
4. 预付费发送前按单价和数量精确冻结金额；余额不足拒绝，且不写入消费账本。
5. 最终可计费结果只确认一次，非计费失败只冲正一次，重复或乱序回执保持幂等。
6. 消费账本和余额审计保留 tenant、业务单/消息、业务类型、通道、价格、数量、金额、状态、交易号、操作人、时间等事实字段。

## Verification boundary

- Backend: service database tests and controller permission contract tests.
- Frontend: React unit tests and local Chrome Playwright scripts.
- Planning: PRD owner validation and UI contract validation for design and production stages.
