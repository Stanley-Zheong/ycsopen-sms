# Phase 26 UI Elements

| Page ID/route | Role/permission | Region | Element/type | Data/validation/format | Action and API effect | States and feedback | data-testid | Obligation/requirement IDs | Behavior IDs | Catalog test/layer | Playwright ID |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| tenant-send `/tenant/send` | TENANT_ADMIN,TENANT_USER,TENANT_DEV | 在线发送 | 页面 | 已审核模板、已审核签名、变量、收件人、预览内容 | GET templates/signatures；POST preview | 只展示可用资源；预览显示最终内容 | tenant-tenant-console-send-page | OBL-F-6-10-A REQ-F-6-10 | tenant-console-send-01 | T-F-6-10-A:playwright | pw-p26-tenant-send-page |
| tenant-send `/tenant/send` | TENANT_ADMIN,TENANT_USER,TENANT_DEV | 在线发送 | 提交按钮 | submitId、手机号、模板、签名、变量 | POST `/api/v1/console/tenant/send` | pending 禁用；成功显示消息 ID | tenant-tenant-console-send-submit | OBL-F-6-10-B REQ-F-6-10 | tenant-console-send-01 | T-F-6-10-B:playwright | pw-p26-tenant-send-submit |
| tenant-send `/tenant/send` | TENANT_ADMIN,TENANT_USER,TENANT_DEV | 网络异常 | 重试按钮 | 原 correlation submitId | POST `/api/v1/console/tenant/send` retry | 显示网络异常，请检查网络后重试；重试成功后清空错误 | shared-tenant-console-send-network-error-retry | OBL-EDGE-NETWORK-TIMEOUT REQ-NFR-ERROR-IDEMPOTENCY | tenant-console-send-02 | T-EDGE-NETWORK-TIMEOUT:playwright | pw-p26-network-retry |

## Additional production test-id inventory

- `tenant-tenant-console-send-template`: 模板选择。
- `tenant-tenant-console-send-signature`: 签名展示。
- `tenant-tenant-console-send-recipients`: 收件人输入。
- `tenant-tenant-console-send-variable-code`: code 变量输入。
- `tenant-tenant-console-send-preview`: 预览区域。
- `tenant-tenant-console-send-correlation`: 提交流水展示。
