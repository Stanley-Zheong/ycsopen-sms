# Phase 26 Spec

## Scope

1. Tenant users can send from `/tenant/send` with JWT console authentication.
2. The page lists only approved templates bound to approved signatures.
3. Template variables can be entered and previewed through the existing canonical preview endpoint.
4. Submit uses the same backend acceptance pipeline as HTTP send through `/api/v1/console/tenant/send`.
5. Single or small-batch submit uses stable submit IDs derived from one correlation identity.
6. Network timeout shows `网络异常，请检查网络后重试` and exposes a retry button that reuses the same correlation identity.

## Obligation trace

| Obligation | Implementation | Verification |
| --- | --- | --- |
| OBL-F-6-10-A | `SendPage`, approved resource filtering, template preview | `send-page.test.tsx`, `send.spec.ts` |
| OBL-F-6-10-B | `TenantConsoleSendController`, `sendTenantConsoleMessage`, submit button | `TenantConsoleSendControllerTest`, `send-page.test.tsx`, `send.spec.ts` |
| OBL-EDGE-NETWORK-TIMEOUT | network failure classifier and retry button | `send-page.test.tsx`, `send.spec.ts` |
