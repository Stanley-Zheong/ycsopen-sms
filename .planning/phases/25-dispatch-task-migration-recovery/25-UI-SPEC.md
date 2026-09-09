# Phase 25 UI Spec

Route: `/admin/channel/health`

Page ID: `admin-channel-health-channel-monitor-page`

新增区域沿用 Phase 11 通道健康页面风格，不新增菜单，不新增 mobile 页面。

## Elements

- `admin-dispatch-task-channel-monitor-task-migration`: 派发任务迁移与恢复卡片，展示待恢复库存表。
- `admin-dispatch-task-channel-monitor-failover`: 刷新故障库存按钮。
- `admin-dispatch-task-channel-monitor-recovery-evidence`: 恢复证据输入框；迁移、重试、恢复测试、恢复通道均复用该证据。
- `admin-dispatch-task-channel-monitor-task-row`: 恢复库存表行。
- `admin-dispatch-task-channel-monitor-migrate`: 对 MIGRATABLE 任务执行迁移。
- `admin-dispatch-task-channel-monitor-retry`: 对 RETRYABLE 任务创建新重试。
- `admin-dispatch-task-channel-monitor-recovery-test`: 小流量恢复测试卡片。
- `admin-dispatch-task-channel-monitor-recovery-test-run`: 对暂停通道记录成功恢复测试。
- `admin-dispatch-task-channel-monitor-recovery-resume`: 成功测试后恢复暂停通道。

## States

- MIGRATABLE: 允许迁移到备用。
- RETRYABLE: 允许创建重试。
- UNCERTAIN: 禁用自动迁移/重试，只展示隔离状态。
- Empty: 显示暂无需要迁移或恢复的任务。
- Error: 显示加载或动作失败信息。
