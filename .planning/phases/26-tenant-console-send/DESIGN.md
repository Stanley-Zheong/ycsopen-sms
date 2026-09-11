# Phase 26 Design

## Backend

- `TenantConsoleSendController` resolves tenant ID from the authenticated user.
- The controller calls `MessageSubmitService.submit(tenantId, null, request, clientIp)`.
- `SecurityConfig` grants `/api/v1/console/tenant/send` to tenant roles.

## Frontend

- `SendPage` loads approved templates and signatures.
- The template dropdown shows only approved templates whose bound signature is approved.
- Variables are rendered from `TemplateRecord.variableNames`.
- Preview uses `/api/v1/console/tenant/templates/{templateId}/preview`.
- Submit uses `/api/v1/console/tenant/send`.
- Network failure shows a retry affordance with `shared-tenant-console-send-network-error-retry`.
