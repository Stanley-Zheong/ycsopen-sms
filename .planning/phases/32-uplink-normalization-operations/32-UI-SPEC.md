# Phase 32 UI Spec

## Admin `/admin/uplink`

- Summary cards: total uplink count and non-delivered push count.
- Filters: tenant, phone number, keyword, carrier, push state, start/end receive time.
- Table: tenant, source, phone, content keyword, state, carrier, destination, location, channel, receive time, actions.
- Detail drawer: message id, content, signature/product, push event linkage.
- Push monitor: destination, state, attempts, latency, update time, replay/pause/resume.

## Tenant `/tenant/uplink`

- Tenant-scoped filters: phone number, keyword, carrier, push state.
- Table: source, masked phone, content keyword, state, carrier, destination, location, receive time.
- Auto-reply config: enabled, keyword, template, response content, loop guard, audit reason, save.

## Style

Reuse existing console card/table/sidebar style. No mobile-specific layout is required.
