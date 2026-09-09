# Phase 29 UI Elements

| Page ID/route | Role/permission | Region | Element/type | Data/validation/format | Action and API effect | States and feedback | data-testid | Obligation/requirement IDs | Behavior IDs | Catalog test/layer | Playwright ID |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| tenant-bulk-send `/tenant/bulk/send` | TENANT_ADMIN,TENANT_USER,TENANT_DEV | 批量导入 | 页面 | CSV/Excel 文件名、大小、安全扫描、模板/签名、变量、号码、重复行 | POST `/api/v1/console/tenant/bulk/preview`; POST `/api/v1/console/tenant/bulk/tasks` | 显示校验成功/失败和创建结果 | tenant-bulk-scheduled-bulk-send-page | OBL-F-6-11-A REQ-F-6-11 | bulk-scheduled-task-operations-02 | T-F-6-11-A:playwright | pw-p29-tenant-bulk-send |
| tenant-bulk-send `/tenant/bulk/send` | TENANT_ADMIN,TENANT_USER,TENANT_DEV | 导入预览 | 校验结果表 | 行号、脱敏手机号、变量、VALID/INVALID、原因 | 以预览快照创建批任务 | 部分失败逐行显示 | tenant-bulk-scheduled-bulk-send-validation-results | OBL-F-6-11-B REQ-F-6-11 | bulk-scheduled-task-operations-02 | T-F-6-11-B:component | pw-p29-import-preview |
| tenant-scheduled-tasks `/tenant/scheduled/tasks` | TENANT_ADMIN,TENANT_USER,TENANT_DEV | 计划任务 | 页面 | 计划时间、优先级、任务状态、进度 | GET `/api/v1/console/tenant/scheduled-tasks` | 展示当前批任务状态 | tenant-bulk-scheduled-scheduled-tasks-page | OBL-F-6-12-A REQ-F-6-12 | bulk-scheduled-task-operations-03 | T-F-6-12-A:playwright | pw-p29-tenant-scheduled-tasks |
| tenant-scheduled-tasks `/tenant/scheduled/tasks` | TENANT_ADMIN,TENANT_USER,TENANT_DEV | 任务控制 | 控制区 | reason、bulkId、action | POST `/api/v1/console/tenant/scheduled-tasks/{bulkId}/{action}` | 非法状态后端拒绝，成功刷新 | tenant-bulk-scheduled-scheduled-tasks-control | OBL-F-6-12-B REQ-F-6-12 | bulk-scheduled-task-operations-03 | T-F-6-12-B:fault | pw-p29-tenant-task-control |
| admin-bulk-details `/admin/bulk/details` | ADMIN,OPERATOR | 批量详情 | 页面 | 总任务、运行、完成、总消息、成本、租户、状态、类型、优先级 | GET `/api/v1/console/bulk/tasks` | 卡片和过滤结果展示 | admin-bulk-scheduled-bulk-details-page | OBL-F-7-3-A REQ-F-7-3 | bulk-scheduled-task-operations-04 | T-F-7-3-A:playwright | pw-p29-admin-bulk-details |
| admin-bulk-details `/admin/bulk/details` | ADMIN,OPERATOR | 批量详情 | 任务与明细表 | taskId、batchKey、tenant、total、progress、success rate、state、priority、cost、item states | GET `/api/v1/console/bulk/tasks/{bulkId}` | 明细状态与任务统计可核对 | admin-bulk-scheduled-bulk-details-table | OBL-F-7-3-B REQ-F-7-3 | bulk-scheduled-task-operations-04 | T-F-7-3-B:integration | pw-p29-admin-bulk-detail-table |
| admin-send-jobs `/admin/send/jobs` | ADMIN,OPERATOR | 发送任务检索 | 页面 | immediate/bulk/scheduled ownership、progress、state | GET `/api/v1/console/bulk/tasks` | 显示可操作任务 | admin-bulk-scheduled-send-jobs-page | OBL-F-13-5-A REQ-F-13-5 | bulk-scheduled-task-operations-05 | T-F-13-5-A:playwright | pw-p29-admin-send-jobs |
| admin-send-jobs `/admin/send/jobs` | ADMIN,OPERATOR | 发送任务控制 | 控制区 | reason、bulkId、action、partial/racing outcome | POST `/api/v1/console/bulk/tasks/{bulkId}/{action}` | 成功显示新状态，失败显示错误 | admin-bulk-scheduled-send-jobs-control | OBL-F-13-5-B REQ-F-13-5 | bulk-scheduled-task-operations-05 | T-F-13-5-B:fault | pw-p29-admin-send-job-control |
| tenant-scheduled-tasks `/tenant/scheduled/tasks` | TENANT_ADMIN,TENANT_USER,TENANT_DEV | 状态机 | 状态单元格 | PENDING/RUNNING/COMPLETED/FAILED | create/reconcile/control | 批任务状态来源于 item 状态 | tenant-bulk-scheduled-scheduled-tasks-state | OBL-STATE-BATCH-START OBL-STATE-BATCH-COMPLETE OBL-STATE-BATCH-FAIL PROJECT-STATE-MACHINE | bulk-scheduled-task-operations-06 | T-STATE-BATCH-START:integration,T-STATE-BATCH-COMPLETE:integration,T-STATE-BATCH-FAIL:fault | pw-p29-state-start,pw-p29-state-complete,pw-p29-state-fail |
| tenant-scheduled-tasks `/tenant/scheduled/tasks` | TENANT_ADMIN,TENANT_USER,TENANT_DEV | 状态机 | 暂停按钮 | RUNNING/PENDING only | POST pause | 成功 PAUSED，非法状态拒绝 | tenant-bulk-scheduled-scheduled-tasks-pause | OBL-STATE-BATCH-PAUSE PROJECT-STATE-MACHINE | bulk-scheduled-task-operations-06 | T-STATE-BATCH-PAUSE:fault | pw-p29-state-pause |
| tenant-scheduled-tasks `/tenant/scheduled/tasks` | TENANT_ADMIN,TENANT_USER,TENANT_DEV | 状态机 | 恢复按钮 | PAUSED only | POST resume | 成功 RUNNING，不重放 terminal items | tenant-bulk-scheduled-scheduled-tasks-resume | OBL-STATE-BATCH-RESUME PROJECT-STATE-MACHINE | bulk-scheduled-task-operations-06 | T-STATE-BATCH-RESUME:fault | pw-p29-state-resume |
| tenant-scheduled-tasks `/tenant/scheduled/tasks` | TENANT_ADMIN,TENANT_USER,TENANT_DEV | 状态机 | 重启按钮 | FAILED only | POST restart | FAILED/CANCELLED 非终态子项恢复为 PENDING | tenant-bulk-scheduled-scheduled-tasks-restart | OBL-STATE-BATCH-RESTART PROJECT-STATE-MACHINE | bulk-scheduled-task-operations-06 | T-STATE-BATCH-RESTART:fault | pw-p29-state-restart |

## Additional production test-id inventory

- `tenant-bulk-scheduled-bulk-send-form`: 批量创建表单。
- `tenant-bulk-scheduled-bulk-send-upload`: 导入内容输入。
- `tenant-bulk-scheduled-bulk-send-file`: CSV/Excel 文件选择器，用于带入文件名和大小元数据。
- `tenant-bulk-scheduled-bulk-send-preview`: 校验预览动作。
- `tenant-bulk-scheduled-bulk-send-create`: 创建批任务动作。
- `tenant-bulk-scheduled-scheduled-tasks-cancel`: 取消任务。
- `admin-bulk-scheduled-bulk-details-cards`: 管理员汇总卡片。
- `admin-bulk-scheduled-bulk-details-items`: 子项详情表。
- `admin-bulk-scheduled-send-jobs-filter`: 运营检索过滤器。
