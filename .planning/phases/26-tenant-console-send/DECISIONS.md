# Phase 26 Decisions

## D1. Thin JWT adapter

The backend endpoint `/api/v1/console/tenant/send` delegates to `MessageSubmitService` instead of duplicating compliance, routing, billing, or idempotency logic.

## D2. Resource filtering is client-visible and server-enforced

The page filters to approved templates bound to approved signatures for usability. Server-side template/signature ownership and approval checks remain in `TemplateSendComplianceService`.

## D3. Correlation identity survives retry

The page creates one console correlation identity and derives recipient submit IDs from it. Network retry does not generate a new submit ID, so Phase23 idempotency remains effective.

## D4. Chrome-only UI verification

Phase26 UI verification uses the local Chrome configured in `web/playwright.config.ts`.
