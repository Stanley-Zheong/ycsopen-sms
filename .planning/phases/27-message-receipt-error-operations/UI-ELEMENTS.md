# Phase 27 UI Elements

| Page ID/route | Role/permission | Region | Element/type | Data/validation/format | Action and API effect | States and feedback | data-testid | Obligation/requirement IDs | Behavior IDs | Catalog test/layer | Playwright ID |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| admin-submission-details `/admin/submission/details` | ADMIN,OPERATOR | 提交详情 | 页面 | 租户、业务流水、消息ID、提交状态、发送状态、模板/签名、错误 | GET `/api/v1/console/message-operations/submissions` | 显示提交链路；错误显示统一错误提示 | admin-message-receipt-submission-details-page | OBL-F-7-1-A REQ-F-7-1 | message-receipt-error-operations-02 | T-F-7-1-A:playwright | pw-p27-submission-page |
| admin-submission-details `/admin/submission/details` | ADMIN,OPERATOR | 提交详情 | 链路表格 | acceptance、risk、route、task、provider、receipt、billing、trace 相关 ID | GET submissions/sends/receipts | 不显示 protected input 明文 | admin-message-receipt-submission-details-trace | OBL-F-7-1-B REQ-F-7-1 | message-receipt-error-operations-02 | T-F-7-1-B:integration | pw-p27-submission-trace |
| admin-send-details `/admin/send/details` | ADMIN,OPERATOR | 发送详情 | 页面 | 手机号、租户、状态、运营商、通道、时间、脱敏字段 | GET `/api/v1/console/message-operations/sends` | 表格显示 maskedMobile=已保护 | admin-message-receipt-send-details-page | OBL-F-7-2-A REQ-F-7-2 | message-receipt-error-operations-03 | T-F-7-2-A:playwright | pw-p27-send-page |
| admin-send-details `/admin/send/details` | ADMIN,OPERATOR | 发送详情 | 重发按钮 | actionId、messageId、reason | POST `/api/v1/console/message-operations/sends/{messageId}/resend` | eligible failed 才可点；成功显示结果 | admin-message-receipt-send-details-resend | OBL-F-7-2-B REQ-F-7-2 | message-receipt-error-operations-03 | T-F-7-2-B:integration | pw-p27-send-resend |
| admin-receipt-details `/admin/receipt/details` | ADMIN,OPERATOR | 回执详情 | 页面 | 手机号、租户、状态、周期、消息、送达时间、错误、通道、运营商、创建时间 | GET `/api/v1/console/message-operations/receipts` | 原始 payload 仅显示摘要 | admin-message-receipt-receipt-details-page | OBL-F-7-4-A REQ-F-7-4 | message-receipt-error-operations-04 | T-F-7-4-A:playwright | pw-p27-receipt-page |
| admin-receipt-details `/admin/receipt/details` | ADMIN,OPERATOR | 回执详情 | 纠正按钮 | receiptId、reason、status、taxonomyVersion | POST `/api/v1/console/message-operations/receipts/{receiptId}/correct` | 保留原始回执并新增纠正回执 | admin-message-receipt-receipt-correct | OBL-F-7-4-B REQ-F-7-4 | message-receipt-error-operations-04 | T-F-7-4-B:integration | pw-p27-receipt-correct |
| admin-error-details `/admin/error/details` | ADMIN,OPERATOR | 错误详情 | 页面 | 归一化错误码、分类、级别、可重试、消息数、租户数、通道数 | GET `/api/v1/console/message-operations/errors` | 按最终失败聚合显示 | admin-message-receipt-error-details-page | OBL-F-7-6-A REQ-F-7-6 | message-receipt-error-operations-05 | T-F-7-6-A:playwright | pw-p27-error-page |
| admin-error-details `/admin/error/details` | ADMIN,OPERATOR | 错误详情 | 批量重试按钮 | actionId、errorCode、messageIds、reason | POST `/api/v1/console/message-operations/errors/actions` | 显示成功/失败部分结果 | admin-message-receipt-error-details-bulk-retry | OBL-F-7-6-B REQ-F-7-6 | message-receipt-error-operations-05 | T-F-7-6-B:integration | pw-p27-error-bulk-retry |

## Additional production test-id inventory

- `admin-message-receipt-filter-tenant`: 租户过滤。
- `admin-message-receipt-filter-message`: 消息ID过滤。
- `admin-message-receipt-filter-status`: 状态过滤。
- `admin-message-receipt-filter-error-code`: 错误码过滤。
- `admin-message-receipt-action-reason`: 动作原因。
- `admin-message-receipt-export-request`: 导出请求 handoff。
- `admin-message-receipt-operation-message`: 成功反馈。
- `admin-message-receipt-operation-error`: 错误反馈。
