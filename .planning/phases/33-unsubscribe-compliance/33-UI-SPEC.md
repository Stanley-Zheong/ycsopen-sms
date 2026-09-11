# Phase 33 UI Spec

## Admin `/admin/unsubscribes`

- Keyword library card: keyword, normalized keyword, scope, tenant, status, save action.
- Evidence list: tenant, masked phone, trigger keyword, signature/product, handling state, notification state, uplink link, unsubscribe time.
- Statistics card: tenant, signature/product, unsubscribe count, final sent count, unsubscribe rate.
- Alert action: threshold input and evaluate button.

## Tenant `/tenant/unsubscribes`

- Tenant-scoped filters: phone, keyword, outcome, signature, product, notification state.
- Evidence table: masked phone, keyword, signature/product, handling, notification, confirmation, uplink, time.
- Export request button that creates a queued export task.
- Tenant keyword card for scoped extension terms.

## Style

Reuse existing console card/table/sidebar style. Chrome is the only supported verification browser. No mobile-specific layout is required.
