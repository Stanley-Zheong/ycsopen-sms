# Phase 25 UI Elements

## Contract rows

| Page ID/route | Role/permission | Region | Element/type | Data/validation/format | Action and API effect | States and feedback | data-testid | Obligation/requirement IDs | Behavior IDs | Catalog test/layer | Playwright ID |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| admin-channel-health-channel-monitor-page `/admin/channel/health` | ADMIN,OPERATOR | 派发任务迁移与恢复 | 任务迁移卡片 | PENDING/READY 任务、原通道、目标通道、恢复状态 | POST `/api/v1/console/dispatch-recovery/tasks/{taskId}/migrate`，任务与 outbox 同步迁移 | 成功提示；重复请求返回既有迁移；不可迁移禁用 | admin-dispatch-task-channel-monitor-task-migration | OBL-F-4-7-C REQ-F-4-7 | dispatch-task-migration-recovery-01 | T-F-4-7-C:fault | pw-p25-task-migration |
| admin-channel-health-channel-monitor-page `/admin/channel/health` | ADMIN,OPERATOR | 小流量恢复测试 | 恢复测试卡片 | 暂停通道、测试结果、证据、恢复入口 | POST `/api/v1/console/dispatch-recovery/channels/{channelId}/recovery-tests` 记录成功测试；POST `/api/v1/console/dispatch-recovery/channels/{channelId}/resume` 恢复为 NORMAL | 成功提示；无成功测试时后端拒绝恢复；无暂停通道显示空状态 | admin-dispatch-task-channel-monitor-recovery-test | OBL-F-4-7-D OBL-STATE-CHANNEL-RECOVER REQ-F-4-7 PROJECT-STATE-MACHINE | dispatch-task-migration-recovery-02 | T-F-4-7-D:playwright,T-STATE-CHANNEL-RECOVER:fault | pw-p25-recovery-test,pw-p25-channel-recover |
| admin-channel-health-channel-monitor-page `/admin/channel/health` | ADMIN,OPERATOR | 派发任务迁移与恢复 | 故障库存刷新按钮 | 故障通道任务库存和 uncertain 状态 | GET `/api/v1/console/dispatch-recovery/inventory`，刷新库存 | 展示 MIGRATABLE、RETRYABLE、UNCERTAIN、OBSERVE | admin-dispatch-task-channel-monitor-failover | OBL-EDGE-UPSTREAM-OUTAGE PROJECT-EXCEPTION-FLOW | dispatch-task-migration-recovery-04 | T-EDGE-UPSTREAM-OUTAGE:fault | pw-p25-upstream-outage |

## Additional production test-id inventory

- `admin-dispatch-task-channel-monitor-recovery-evidence`: 恢复证据输入框。
- `admin-dispatch-task-channel-monitor-task-row`: 派发恢复库存表行。
- `admin-dispatch-task-channel-monitor-migrate`: 迁移到备用按钮。
- `admin-dispatch-task-channel-monitor-retry`: 创建重试按钮。
- `admin-dispatch-task-channel-monitor-recovery-test-run`: 记录恢复测试按钮。
- `admin-dispatch-task-channel-monitor-recovery-resume`: 成功测试后恢复暂停通道按钮。
